package sg.edu.nus.iss.c2csectrade.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.iss.c2csectrade.entity.Order;
import sg.edu.nus.iss.c2csectrade.entity.OrderAction;
import sg.edu.nus.iss.c2csectrade.entity.OrderStatus;
import sg.edu.nus.iss.c2csectrade.mapper.OrderMapper;
import sg.edu.nus.iss.c2csectrade.service.OrderService;
import sg.edu.nus.iss.c2csectrade.service.OrderStateService;
import sg.edu.nus.iss.c2csectrade.service.PaymentService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What happens when two things reach the same order, or the same last unit of
 * stock, at the same moment (#69).
 *
 * These tests commit, so every one of them runs outside the surrounding
 * rollback and cleans up after itself. Each racing call runs on its own thread
 * and therefore in its own transaction, which is the only way the conditional
 * updates being relied on are actually exercised.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PaymentConcurrencyIT extends MySqlIntegrationTest {

    // 《算法导论》, 55.00, stock 1, sold by 102.
    private static final Long PRODUCT = 4L;
    private static final Long BUYER = 1L;
    private static final Long OTHER_BUYER = 103L;
    private static final Long SELLER = 102L;
    private static final BigDecimal PRICE = new BigDecimal("55.00");
    private static final String PAYMENT_PASSWORD_HASH =
            "$2a$12$qKa0QH..hifzPlSsE6U.Q.4gGjw513N7.xWFpmr5Q.cp12YhGY7lG";

    @Autowired JdbcTemplate jdbc;
    @Autowired OrderService orders;
    @Autowired PaymentService payments;
    @Autowired OrderStateService transitions;
    @Autowired OrderMapper orderMapper;

    @BeforeEach
    void fundTheBuyers() {
        for (Long buyer : List.of(BUYER, OTHER_BUYER)) {
            jdbc.update("UPDATE users SET balance=?, payment_password_hash=? WHERE id=?",
                    new BigDecimal("500.00"), PAYMENT_PASSWORD_HASH, buyer);
        }
        jdbc.update("UPDATE users SET balance=? WHERE id=?", new BigDecimal("2000.00"), SELLER);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM oms_transaction WHERE order_id IN "
                + "(SELECT id FROM oms_order WHERE buyer_id IN (?, ?))", BUYER, OTHER_BUYER);
        jdbc.update("DELETE FROM oms_order_item WHERE order_id IN "
                + "(SELECT id FROM oms_order WHERE buyer_id IN (?, ?))", BUYER, OTHER_BUYER);
        jdbc.update("DELETE FROM oms_order WHERE buyer_id IN (?, ?)", BUYER, OTHER_BUYER);
        jdbc.update("UPDATE pms_product SET stock=1, reserved_stock=0 WHERE id=?", PRODUCT);
        jdbc.update("UPDATE users SET balance=? WHERE id=?", new BigDecimal("2000.00"), SELLER);
    }

    /** Runs both tasks at once and returns the exception each threw, or null. */
    private List<Throwable> race(Runnable first, Runnable second) throws Exception {
        CountDownLatch startLine = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Throwable>> futures = new ArrayList<>();
            for (Runnable task : List.of(first, second)) {
                futures.add(pool.submit(() -> {
                    startLine.await();
                    try {
                        task.run();
                        return null;
                    } catch (Throwable thrown) {
                        return thrown;
                    }
                }));
            }
            startLine.countDown();
            List<Throwable> outcomes = new ArrayList<>();
            for (Future<Throwable> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private int countPayments(Long orderId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM oms_transaction WHERE order_id=? AND transaction_type='PAYMENT'",
                Integer.class, orderId);
    }

    private int countPayouts(Long orderId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM oms_transaction WHERE order_id=? AND transaction_type='PAYOUT'",
                Integer.class, orderId);
    }

    private BigDecimal balanceOf(Long userId) {
        return jdbc.queryForObject("SELECT balance FROM users WHERE id=?", BigDecimal.class, userId);
    }

    private int stock() {
        return jdbc.queryForObject("SELECT stock FROM pms_product WHERE id=?", Integer.class, PRODUCT);
    }

    private int reserved() {
        return jdbc.queryForObject("SELECT reserved_stock FROM pms_product WHERE id=?",
                Integer.class, PRODUCT);
    }

    @Test
    @DisplayName("Two payments on the same order: one succeeds, the other is refused, one charge lands")
    void onlyOnePaymentOnOneOrderSucceeds() throws Exception {
        Order order = orders.checkoutDirect(BUYER, PRODUCT, 1);
        BigDecimal before = jdbc.queryForObject(
                "SELECT balance FROM users WHERE id=?", BigDecimal.class, BUYER);

        List<Throwable> outcomes = race(
                () -> payments.pay(BUYER, order.getId(), "balance", "123456"),
                () -> payments.pay(BUYER, order.getId(), "balance", "123456"));

        long failed = outcomes.stream().filter(java.util.Objects::nonNull).count();
        assertEquals(1, failed, "exactly one of the two attempts must be refused: " + outcomes);
        assertEquals(1, countPayments(order.getId()), "the order must carry one payment row");
        assertEquals(OrderStatus.PAID.name(), orderMapper.selectById(order.getId()).getStatus());

        BigDecimal after = jdbc.queryForObject(
                "SELECT balance FROM users WHERE id=?", BigDecimal.class, BUYER);
        assertEquals(0, before.subtract(new BigDecimal("55.00")).compareTo(after),
                "the buyer must be charged once, not twice");
        assertEquals(0, stock(), "the single unit is sold");
        assertEquals(0, reserved(), "the reservation became a deduction, it was not left behind");
    }

    @Test
    @DisplayName("Paying while the expiry sweep runs leaves one outcome, not both")
    void paymentAndExpiryCannotBothWin() throws Exception {
        Order order = orders.checkoutDirect(BUYER, PRODUCT, 1);
        // Put the window in the past so the sweep picks this order up.
        jdbc.update("UPDATE oms_order SET expire_at = NOW() - INTERVAL 1 MINUTE WHERE id=?",
                order.getId());

        race(() -> payments.pay(BUYER, order.getId(), "balance", "123456"),
                () -> orders.expireOverdueOrders());

        String status = orderMapper.selectById(order.getId()).getStatus();
        if (OrderStatus.PAID.name().equals(status)) {
            assertEquals(1, countPayments(order.getId()));
            assertEquals(0, stock(), "a paid order keeps its stock deducted");
            assertEquals(0, reserved(), "expiry must not release what payment already deducted");
        } else {
            assertEquals(OrderStatus.EXPIRED.name(), status,
                    "the order must end up either paid or expired, nothing else");
            assertEquals(0, countPayments(order.getId()), "an expired order was never charged");
            assertEquals(1, stock(), "an expired order hands its unit back");
            assertEquals(0, reserved());
        }
    }

    @Test
    @DisplayName("Two accepted offers on the last unit: only one becomes an order")
    void onlyOneNegotiatedCheckoutGetsTheLastUnit() throws Exception {
        List<Throwable> outcomes = race(
                () -> orders.checkoutNegotiated(BUYER, PRODUCT, new BigDecimal("40.00")),
                () -> orders.checkoutNegotiated(OTHER_BUYER, PRODUCT, new BigDecimal("42.00")));

        long failed = outcomes.stream().filter(java.util.Objects::nonNull).count();
        assertEquals(1, failed, "the second accept must be refused: " + outcomes);
        assertEquals(1, jdbc.queryForObject(
                        "SELECT COUNT(*) FROM oms_order WHERE buyer_id IN (?, ?)", Integer.class,
                        BUYER, OTHER_BUYER),
                "one unit of stock can only become one order");
        assertEquals(1, reserved(), "exactly one reservation is held");
        assertEquals(1, stock(), "an unpaid order reserves, it does not deduct");
    }

    @Test
    @DisplayName("Refunding while the seller dispatches: the money ends up in exactly one place")
    void refundAndDispatchCannotBothWin() throws Exception {
        Order order = orders.checkoutDirect(BUYER, PRODUCT, 1);
        payments.pay(BUYER, order.getId(), "balance", "123456");
        BigDecimal buyerAfterPaying = balanceOf(BUYER);
        BigDecimal sellerBefore = balanceOf(SELLER);

        // Both leave PAID, so they are the two transitions that can actually
        // collide once money is held against an order.
        List<Throwable> outcomes = race(
                () -> payments.refund(SELLER, order.getId()),
                () -> transitions.apply(SELLER, order.getId(), OrderAction.SHIP));

        assertEquals(1, outcomes.stream().filter(java.util.Objects::nonNull).count(),
                "exactly one of refund and ship must be refused: " + outcomes);

        String status = orderMapper.selectById(order.getId()).getStatus();
        if (OrderStatus.REFUNDED.name().equals(status)) {
            assertEquals(0, buyerAfterPaying.add(PRICE).compareTo(balanceOf(BUYER)),
                    "the refund gave the held money back");
            assertEquals(1, stock(), "a refunded order puts its unit back");
            assertEquals(0, countPayouts(order.getId()), "nobody was paid out");
        } else {
            assertEquals(OrderStatus.SHIPPED.name(), status,
                    "the order must end up either refunded or shipped, nothing else");
            assertEquals(0, buyerAfterPaying.compareTo(balanceOf(BUYER)),
                    "a shipped order has not been refunded");
            assertEquals(0, stock(), "the unit stays sold");
        }
        assertEquals(0, sellerBefore.compareTo(balanceOf(SELLER)),
                "either way the seller is not paid until receipt is confirmed");
    }

    @Test
    @DisplayName("Two confirmations of the same delivery: the seller is paid once")
    void onlyOneConfirmationPaysTheSeller() throws Exception {
        Order order = orders.checkoutDirect(BUYER, PRODUCT, 1);
        payments.pay(BUYER, order.getId(), "balance", "123456");
        transitions.apply(SELLER, order.getId(), OrderAction.SHIP);
        BigDecimal sellerBefore = balanceOf(SELLER);

        List<Throwable> outcomes = race(
                () -> transitions.apply(BUYER, order.getId(), OrderAction.CONFIRM_RECEIPT),
                () -> transitions.apply(BUYER, order.getId(), OrderAction.CONFIRM_RECEIPT));

        assertEquals(1, outcomes.stream().filter(java.util.Objects::nonNull).count(),
                "the second confirmation must be refused: " + outcomes);
        assertEquals(OrderStatus.COMPLETED.name(), orderMapper.selectById(order.getId()).getStatus());
        assertEquals(1, countPayouts(order.getId()), "one payout row, not two");
        assertEquals(0, sellerBefore.add(PRICE).compareTo(balanceOf(SELLER)),
                "the seller is paid once");
    }

    @Test
    @DisplayName("Two buyers checking out the last unit the ordinary way: one is refused")
    void onlyOneDirectCheckoutGetsTheLastUnit() throws Exception {
        List<Throwable> outcomes = race(
                () -> orders.checkoutDirect(BUYER, PRODUCT, 1),
                () -> orders.checkoutDirect(OTHER_BUYER, PRODUCT, 1));

        assertEquals(1, outcomes.stream().filter(java.util.Objects::nonNull).count(),
                "exactly one checkout must be refused: " + outcomes);
        assertEquals(1, reserved());
    }
}

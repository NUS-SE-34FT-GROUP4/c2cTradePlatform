package sg.edu.nus.iss.c2csectrade.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import sg.edu.nus.iss.c2csectrade.entity.Order;
import sg.edu.nus.iss.c2csectrade.entity.OrderAction;
import sg.edu.nus.iss.c2csectrade.entity.OrderStatus;
import sg.edu.nus.iss.c2csectrade.entity.Transaction;
import sg.edu.nus.iss.c2csectrade.entity.TransactionType;
import sg.edu.nus.iss.c2csectrade.mapper.OrderMapper;
import sg.edu.nus.iss.c2csectrade.mapper.TransactionMapper;
import sg.edu.nus.iss.c2csectrade.security.JwtTokenProvider;
import sg.edu.nus.iss.c2csectrade.service.OrderService;
import sg.edu.nus.iss.c2csectrade.service.OrderStateService;
import sg.edu.nus.iss.c2csectrade.service.PaymentService;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Holding the buyer's money until the buyer confirms receipt (#109).
 *
 * The thing worth checking against a real database is who has the money at
 * each step, so every test here reads the two balances and the ledger rather
 * than only the order's status.
 */
class EscrowIT extends MySqlIntegrationTest {

    // 《算法导论》, 55.00, stock 1, sold by 102.
    private static final Long PRODUCT = 4L;
    private static final Long BUYER = 1L;
    private static final Long SELLER = 102L;
    private static final BigDecimal PRICE = new BigDecimal("55.00");
    private static final BigDecimal BUYER_FUNDS = new BigDecimal("500.00");
    private static final BigDecimal SELLER_FUNDS = new BigDecimal("2000.00");
    // init.sql: the payment password for every seeded account is 123456.
    private static final String PAYMENT_PASSWORD_HASH =
            "$2a$12$qKa0QH..hifzPlSsE6U.Q.4gGjw513N7.xWFpmr5Q.cp12YhGY7lG";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderService orders;
    @Autowired PaymentService payments;
    @Autowired OrderStateService transitions;
    @Autowired OrderMapper orderMapper;
    @Autowired TransactionMapper ledger;
    @Autowired JwtTokenProvider tokens;

    @BeforeEach
    void fundBothParties() {
        jdbc.update("UPDATE users SET balance=?, payment_password_hash=? WHERE id=?",
                BUYER_FUNDS, PAYMENT_PASSWORD_HASH, BUYER);
        jdbc.update("UPDATE users SET balance=? WHERE id=?", SELLER_FUNDS, SELLER);
    }

    private Order paidOrder() {
        Order order = orders.checkoutDirect(BUYER, PRODUCT, 1);
        payments.pay(BUYER, order.getId(), "balance", "123456");
        return order;
    }

    private BigDecimal balanceOf(Long userId) {
        return jdbc.queryForObject("SELECT balance FROM users WHERE id=?", BigDecimal.class, userId);
    }

    private BigDecimal amountOf(Long orderId, TransactionType type) {
        return ledger.selectByOrderId(orderId).stream()
                .filter(movement -> movement.typeAsEnum() == type)
                .map(Transaction::getAmount)
                .findFirst()
                .orElse(null);
    }

    @Test
    @DisplayName("Paying takes the money from the buyer and gives it to nobody")
    void paymentHoldsTheMoney() {
        Order order = paidOrder();

        assertEquals(0, BUYER_FUNDS.subtract(PRICE).compareTo(balanceOf(BUYER)),
                "the buyer has paid");
        assertEquals(0, SELLER_FUNDS.compareTo(balanceOf(SELLER)),
                "the seller has not been paid");
        assertEquals(0, PRICE.compareTo(payments.heldAmount(order.getId())),
                "the order holds what was paid");
        assertEquals(1, ledger.selectByOrderId(order.getId()).size(),
                "one movement so far: the payment");
    }

    @Test
    @DisplayName("Dispatching does not pay the seller; the money is still held")
    void shippingKeepsTheMoneyHeld() {
        Order order = paidOrder();

        transitions.apply(SELLER, order.getId(), OrderAction.SHIP);

        assertEquals(OrderStatus.SHIPPED.name(), orderMapper.selectById(order.getId()).getStatus());
        assertEquals(0, SELLER_FUNDS.compareTo(balanceOf(SELLER)));
        assertEquals(0, PRICE.compareTo(payments.heldAmount(order.getId())));
    }

    @Test
    @DisplayName("Confirming receipt pays the seller and records the payout beside the payment")
    void confirmingReceiptPaysTheSeller() {
        Order order = paidOrder();
        transitions.apply(SELLER, order.getId(), OrderAction.SHIP);

        transitions.apply(BUYER, order.getId(), OrderAction.CONFIRM_RECEIPT);

        assertEquals(OrderStatus.COMPLETED.name(), orderMapper.selectById(order.getId()).getStatus());
        assertEquals(0, SELLER_FUNDS.add(PRICE).compareTo(balanceOf(SELLER)),
                "the seller now has the money");
        assertEquals(0, BUYER_FUNDS.subtract(PRICE).compareTo(balanceOf(BUYER)),
                "the buyer paid once, not twice");
        assertEquals(0, payments.heldAmount(order.getId()).signum(), "nothing is held any more");

        List<Transaction> movements = ledger.selectByOrderId(order.getId());
        assertEquals(2, movements.size(), "the payment and the payout");
        assertEquals(PRICE, amountOf(order.getId(), TransactionType.PAYMENT),
                "the payment row is still there, unchanged");
        assertEquals(PRICE, amountOf(order.getId(), TransactionType.PAYOUT));
    }

    @Test
    @DisplayName("Once the money is the seller's it cannot be refunded")
    void completedOrderCannotBeRefunded() {
        Order order = paidOrder();
        transitions.apply(SELLER, order.getId(), OrderAction.SHIP);
        transitions.apply(BUYER, order.getId(), OrderAction.CONFIRM_RECEIPT);

        assertThrows(IllegalStateException.class, () -> payments.refund(SELLER, order.getId()));

        assertEquals(2, ledger.selectByOrderId(order.getId()).size(), "no refund row was written");
        assertEquals(0, SELLER_FUNDS.add(PRICE).compareTo(balanceOf(SELLER)),
                "the seller keeps what they were paid");
    }

    @Test
    @DisplayName("A second confirmation is refused and does not pay the seller twice")
    void secondConfirmationPaysNothingMore() {
        Order order = paidOrder();
        transitions.apply(SELLER, order.getId(), OrderAction.SHIP);
        transitions.apply(BUYER, order.getId(), OrderAction.CONFIRM_RECEIPT);

        assertThrows(IllegalStateException.class,
                () -> transitions.apply(BUYER, order.getId(), OrderAction.CONFIRM_RECEIPT));

        assertEquals(0, SELLER_FUNDS.add(PRICE).compareTo(balanceOf(SELLER)));
        assertEquals(1, ledger.selectByOrderId(order.getId()).stream()
                .filter(movement -> movement.typeAsEnum() == TransactionType.PAYOUT).count());
    }

    @Test
    @DisplayName("An external method holds the money on the same schedule as a balance payment")
    void gatewayPaymentFollowsTheSameTiming() {
        Order order = orders.checkoutDirect(BUYER, PRODUCT, 1);

        payments.pay(BUYER, order.getId(), "alipay", "123456");

        assertEquals(0, BUYER_FUNDS.compareTo(balanceOf(BUYER)),
                "the money came from outside, so the balance is untouched (NFR 6.4)");
        assertEquals(0, SELLER_FUNDS.compareTo(balanceOf(SELLER)), "the seller is not paid yet");

        transitions.apply(SELLER, order.getId(), OrderAction.SHIP);
        transitions.apply(BUYER, order.getId(), OrderAction.CONFIRM_RECEIPT);

        assertEquals(0, SELLER_FUNDS.add(PRICE).compareTo(balanceOf(SELLER)),
                "and is paid on the same signal as a balance payment");
        assertEquals(0, payments.heldAmount(order.getId()).signum());
    }

    @Test
    @DisplayName("Both parties can see what is held, so the demo can show it")
    void heldAmountIsOnTheOrderForBothParties() throws Exception {
        Order order = paidOrder();

        for (String account : List.of("admin", "seller_lvl2")) {
            mvc.perform(get("/api/orders/" + order.getId())
                            .header("Authorization", "Bearer " + tokens.generateTokenForUsername(account)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(OrderStatus.PAID.name()))
                    .andExpect(jsonPath("$.heldAmount").value(55.00));
        }

        transitions.apply(SELLER, order.getId(), OrderAction.SHIP);
        transitions.apply(BUYER, order.getId(), OrderAction.CONFIRM_RECEIPT);

        mvc.perform(get("/api/orders/" + order.getId())
                        .header("Authorization", "Bearer " + tokens.generateTokenForUsername("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(OrderStatus.COMPLETED.name()))
                .andExpect(jsonPath("$.heldAmount").value(0));
    }
}

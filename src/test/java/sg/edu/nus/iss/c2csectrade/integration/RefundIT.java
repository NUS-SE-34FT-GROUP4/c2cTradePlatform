package sg.edu.nus.iss.c2csectrade.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import sg.edu.nus.iss.c2csectrade.entity.Order;
import sg.edu.nus.iss.c2csectrade.entity.OrderStatus;
import sg.edu.nus.iss.c2csectrade.entity.Transaction;
import sg.edu.nus.iss.c2csectrade.entity.TransactionType;
import sg.edu.nus.iss.c2csectrade.mapper.OrderMapper;
import sg.edu.nus.iss.c2csectrade.mapper.TransactionMapper;
import sg.edu.nus.iss.c2csectrade.security.JwtTokenProvider;
import sg.edu.nus.iss.c2csectrade.service.OrderService;
import sg.edu.nus.iss.c2csectrade.service.PaymentService;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Refunding a paid order against the real schema (#68).
 *
 * The point of doing this on a real database is the ledger: that the payment
 * row is still there afterwards, untouched, with the refund beside it.
 */
class RefundIT extends MySqlIntegrationTest {

    // 《算法导论》, 55.00, stock 1, sold by 102.
    private static final Long PRODUCT = 4L;
    private static final Long BUYER = 1L;
    private static final Long SELLER = 102L;
    private static final BigDecimal PRICE = new BigDecimal("55.00");
    // init.sql: the payment password for every seeded account is 123456.
    private static final String PAYMENT_PASSWORD_HASH =
            "$2a$12$qKa0QH..hifzPlSsE6U.Q.4gGjw513N7.xWFpmr5Q.cp12YhGY7lG";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderService orders;
    @Autowired PaymentService payments;
    @Autowired OrderMapper orderMapper;
    @Autowired TransactionMapper transactions;
    @Autowired JwtTokenProvider tokens;

    @BeforeEach
    void giveTheBuyerMoneyAndAPaymentPassword() {
        jdbc.update("UPDATE users SET balance=?, payment_password_hash=? WHERE id=?",
                new BigDecimal("500.00"), PAYMENT_PASSWORD_HASH, BUYER);
        jdbc.update("UPDATE users SET balance=? WHERE id=?", new BigDecimal("2000.00"), SELLER);
    }

    private Order paidOrder() {
        Order order = orders.checkoutDirect(BUYER, PRODUCT, 1);
        payments.pay(BUYER, order.getId(), "balance", "123456");
        return order;
    }

    private BigDecimal balanceOf(Long userId) {
        return jdbc.queryForObject("SELECT balance FROM users WHERE id=?", BigDecimal.class, userId);
    }

    private int stockOf(Long productId) {
        return jdbc.queryForObject("SELECT stock FROM pms_product WHERE id=?", Integer.class, productId);
    }

    @Test
    @DisplayName("The refund is a second row; the payment row survives it unchanged")
    void ledgerKeepsBothRows() {
        Order order = paidOrder();
        Transaction paymentBefore = transactions.selectByOrderId(order.getId()).get(0);

        payments.refund(SELLER, order.getId());

        List<Transaction> ledger = transactions.selectByOrderId(order.getId());
        assertEquals(2, ledger.size(), "payment and refund should both be on the order");

        Transaction payment = ledger.stream()
                .filter(t -> t.typeAsEnum() == TransactionType.PAYMENT).findFirst().orElseThrow();
        assertEquals(paymentBefore.getId(), payment.getId(), "the payment row was replaced, not kept");
        assertEquals(PRICE, payment.getAmount());
        assertEquals("SUCCESS", payment.getStatus());

        Transaction refund = ledger.stream()
                .filter(t -> t.typeAsEnum() == TransactionType.REFUND).findFirst().orElseThrow();
        assertEquals(PRICE, refund.getAmount());
        assertEquals("balance", refund.getPaymentMethod());
        assertNotEquals(payment.getId(), refund.getId());
    }

    @Test
    @DisplayName("Refunding moves the money back and puts the listing back on the shelf")
    void moneyAndStockComeBack() {
        BigDecimal buyerBefore = balanceOf(BUYER);
        BigDecimal sellerBefore = balanceOf(SELLER);

        Order order = paidOrder();
        assertEquals(0, stockOf(PRODUCT), "payment should have deducted the only unit");

        payments.refund(SELLER, order.getId());

        assertEquals(0, buyerBefore.compareTo(balanceOf(BUYER)), "buyer is whole again");
        assertEquals(0, sellerBefore.compareTo(balanceOf(SELLER)), "seller gave it back");
        assertEquals(1, stockOf(PRODUCT), "the unit is for sale again");
        assertEquals(0, jdbc.queryForObject(
                "SELECT reserved_stock FROM pms_product WHERE id=?", Integer.class, PRODUCT),
                "a refund restores sold stock, it does not re-reserve it");
        assertEquals(OrderStatus.REFUNDED.name(),
                orderMapper.selectById(order.getId()).getStatus());
    }

    @Test
    @DisplayName("A second refund is refused and leaves the ledger and the stock alone")
    void secondRefundIsRefused() {
        Order order = paidOrder();
        payments.refund(SELLER, order.getId());

        assertThrows(IllegalStateException.class, () -> payments.refund(SELLER, order.getId()));

        assertEquals(2, transactions.selectByOrderId(order.getId()).size());
        assertEquals(1, stockOf(PRODUCT), "the stock was restored once, not twice");
    }

    @Test
    @DisplayName("The seller's refund endpoint answers; another account's does not")
    void endpointIsSellerOnly() throws Exception {
        Order order = paidOrder();

        // The buyer is authenticated but is not this order's seller.
        mvc.perform(post("/api/orders/" + order.getId() + "/refund")
                        .header("Authorization", "Bearer " + tokens.generateTokenForUsername("admin")))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/orders/" + order.getId() + "/refund")
                        .header("Authorization", "Bearer " + tokens.generateTokenForUsername("seller_lvl2")))
                .andExpect(status().isOk());

        assertEquals(OrderStatus.REFUNDED.name(),
                orderMapper.selectById(order.getId()).getStatus());
    }

    @Test
    @DisplayName("An unpaid order cannot be refunded at all")
    void unpaidOrderIsRefused() {
        Order order = orders.checkoutDirect(BUYER, PRODUCT, 1);

        assertThrows(IllegalStateException.class, () -> payments.refund(SELLER, order.getId()));

        assertTrue(transactions.selectByOrderId(order.getId()).isEmpty());
        assertEquals(OrderStatus.PENDING_PAYMENT.name(),
                orderMapper.selectById(order.getId()).getStatus());
    }
}

package sg.edu.nus.iss.c2csectrade.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sg.edu.nus.iss.c2csectrade.entity.*;
import sg.edu.nus.iss.c2csectrade.exception.InsufficientBalanceException;
import sg.edu.nus.iss.c2csectrade.mapper.*;
import sg.edu.nus.iss.c2csectrade.service.payment.AlipayPaymentStrategy;
import sg.edu.nus.iss.c2csectrade.service.payment.BalancePaymentStrategy;
import sg.edu.nus.iss.c2csectrade.service.payment.PayNowPaymentStrategy;
import sg.edu.nus.iss.c2csectrade.service.payment.PaymentStrategy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Sprint 3 acceptance checks for paying an order. */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private OrderMapper orderMapper;
    @Mock private OrderItemMapper orderItemMapper;
    @Mock private ProductMapper productMapper;
    @Mock private TransactionMapper transactionMapper;
    @Mock private WalletService walletService;

    private PaymentService paymentService;

    private static final Long BUYER = 10L;
    private static final Long SELLER = 20L;
    private static final Long ORDER = 500L;

    @BeforeEach
    void setUp() {
        PaymentStrategy balance = new BalancePaymentStrategy(walletService);
        paymentService = new PaymentService(orderMapper, orderItemMapper, productMapper,
                transactionMapper, event -> { }, List.of(balance));
    }

    private Order pendingOrder() {
        Order order = new Order();
        order.setId(ORDER);
        order.setOrderNo("20260930000001");
        order.setBuyerId(BUYER);
        order.setSellerId(SELLER);
        order.setStatus(OrderStatus.PENDING_PAYMENT.name());
        order.setTotalAmount(new BigDecimal("30.00"));
        return order;
    }

    private void stubPayableOrder() {
        when(orderMapper.selectById(ORDER)).thenReturn(pendingOrder());
        lenient().when(orderMapper.transition(eq(ORDER), anyString(), anyString(), anyString()))
                .thenReturn(1);
        OrderItem item = new OrderItem();
        item.setProductId(101L);
        item.setQuantity(2);
        item.setProductName("Textbook");
        lenient().when(orderItemMapper.selectByOrderId(ORDER)).thenReturn(List.of(item));
        lenient().when(productMapper.decreaseStock(anyLong(), anyInt())).thenReturn(1);
    }

    @Test
    @DisplayName("Paying by balance debits the buyer and holds the money; the seller is not paid yet")
    void balancePaymentHoldsTheMoneyAgainstTheOrder() {
        stubPayableOrder();

        paymentService.pay(BUYER, ORDER, "balance", "123456");

        verify(walletService).pay(BUYER, "123456", new BigDecimal("30.00"));
        verify(walletService, never()).credit(anyLong(), any());

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionMapper).insert(captor.capture());
        Transaction recorded = captor.getValue();
        assertEquals("balance", recorded.getPaymentMethod());
        assertEquals(TransactionType.PAYMENT.name(), recorded.getTransactionType());
        assertEquals("SUCCESS", recorded.getStatus());
    }

    @Test
    @DisplayName("A successful payment turns the reservation into a real deduction")
    void paymentConvertsReservation() {
        stubPayableOrder();

        paymentService.pay(BUYER, ORDER, "balance", "123456");

        verify(productMapper).releaseStock(101L, 2);
        verify(productMapper).decreaseStock(101L, 2);
    }

    @Test
    @DisplayName("Insufficient balance records nothing and moves no money at all")
    void failedSettlementRecordsNothing() {
        stubPayableOrder();
        doThrow(new InsufficientBalanceException("Not enough balance"))
                .when(walletService).pay(anyLong(), anyString(), any());

        assertThrows(InsufficientBalanceException.class,
                () -> paymentService.pay(BUYER, ORDER, "balance", "123456"));

        verify(walletService, never()).credit(anyLong(), any());
        verify(transactionMapper, never()).insert(any());
        verify(productMapper, never()).decreaseStock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("A second payment on the same order is refused before any money moves")
    void doublePaymentIsRefused() {
        when(orderMapper.selectById(ORDER)).thenReturn(pendingOrder());
        // Someone else's request claimed the order first, so the conditional
        // update changes no row.
        when(orderMapper.transition(eq(ORDER), anyString(), anyString(), anyString())).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> paymentService.pay(BUYER, ORDER, "balance", "123456"));

        verifyNoInteractions(walletService);
        verify(transactionMapper, never()).insert(any());
    }

    @Test
    @DisplayName("An order that is not awaiting payment cannot be paid")
    void alreadyPaidOrderIsRefused() {
        Order paid = pendingOrder();
        paid.setStatus(OrderStatus.PAID.name());
        when(orderMapper.selectById(ORDER)).thenReturn(paid);

        assertThrows(IllegalStateException.class,
                () -> paymentService.pay(BUYER, ORDER, "balance", "123456"));
        verifyNoInteractions(walletService);
    }

    @Test
    @DisplayName("Another buyer cannot pay for someone else's order")
    void otherBuyerIsRefused() {
        when(orderMapper.selectById(ORDER)).thenReturn(pendingOrder());

        assertThrows(IllegalArgumentException.class,
                () -> paymentService.pay(999L, ORDER, "balance", "123456"));
        verifyNoInteractions(walletService);
    }

    @Test
    @DisplayName("An unknown payment method is rejected")
    void unknownMethodIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> paymentService.pay(BUYER, ORDER, "bitcoin", "123456"));
    }

    // ---- Sprint 4: refunding a paid order (#68) ----

    private Order paidOrder() {
        Order order = pendingOrder();
        order.setStatus(OrderStatus.PAID.name());
        return order;
    }

    private Transaction paymentRow(String method, String amount) {
        Transaction paid = new Transaction();
        paid.setId(9000L);
        paid.setOrderId(ORDER);
        paid.setAmount(new BigDecimal(amount));
        paid.setPaymentMethod(method);
        paid.setTransactionType(TransactionType.PAYMENT.name());
        paid.setStatus("SUCCESS");
        paid.setCreatedAt(LocalDateTime.of(2026, 10, 17, 9, 0));
        return paid;
    }

    private void stubRefundableOrder(String method, String amount) {
        when(orderMapper.selectById(ORDER)).thenReturn(paidOrder());
        lenient().when(orderMapper.transition(eq(ORDER), anyString(), anyString(), isNull()))
                .thenReturn(1);
        lenient().when(transactionMapper.selectByOrderId(ORDER))
                .thenReturn(List.of(paymentRow(method, amount)));
        OrderItem item = new OrderItem();
        item.setProductId(101L);
        item.setQuantity(2);
        item.setProductName("Textbook");
        lenient().when(orderItemMapper.selectByOrderId(ORDER)).thenReturn(List.of(item));
    }

    @Test
    @DisplayName("A refund is a new ledger row; the payment row is never touched")
    void refundIsAppendOnly() {
        stubRefundableOrder("balance", "30.00");

        paymentService.refund(SELLER, ORDER);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionMapper).insert(captor.capture());
        Transaction recorded = captor.getValue();
        assertEquals(TransactionType.REFUND.name(), recorded.getTransactionType());
        assertEquals(new BigDecimal("30.00"), recorded.getAmount());
        assertEquals("balance", recorded.getPaymentMethod());
        assertEquals("SUCCESS", recorded.getStatus());
        // Nothing else on the mapper: no update, no delete of the payment row.
        verify(transactionMapper).selectByOrderId(ORDER);
        verifyNoMoreInteractions(transactionMapper);
    }

    @Test
    @DisplayName("Refunding a balance payment returns the held money to the buyer")
    void balanceRefundReturnsTheHeldMoney() {
        stubRefundableOrder("balance", "30.00");

        paymentService.refund(SELLER, ORDER);

        verify(walletService).credit(BUYER, new BigDecimal("30.00"));
        // The seller was never paid, so a refund cannot fail on their balance.
        verify(walletService, never()).debit(anyLong(), any());
    }

    @Test
    @DisplayName("A refund puts the sold stock back, not the reservation")
    void refundRestoresStock() {
        stubRefundableOrder("balance", "30.00");

        paymentService.refund(SELLER, ORDER);

        verify(productMapper).restoreStock(101L, 2);
        verify(productMapper, never()).reserveStock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("The refunded amount comes from the ledger, not from the order total")
    void refundUsesTheAmountActuallyPaid() {
        // A bargain was accepted at 18.00 while the order total says 30.00.
        stubRefundableOrder("balance", "18.00");

        paymentService.refund(SELLER, ORDER);

        verify(walletService).credit(BUYER, new BigDecimal("18.00"));
    }

    @Test
    @DisplayName("A second refund on the same order is refused before any money moves")
    void doubleRefundIsRefused() {
        when(orderMapper.selectById(ORDER)).thenReturn(paidOrder());
        // The first refund already claimed the transition, so this one changes no row.
        when(orderMapper.transition(eq(ORDER), anyString(), anyString(), isNull())).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> paymentService.refund(SELLER, ORDER));

        verifyNoInteractions(walletService);
        verify(transactionMapper, never()).insert(any());
        verify(productMapper, never()).restoreStock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("An order that was never paid cannot be refunded")
    void unpaidOrderCannotBeRefunded() {
        when(orderMapper.selectById(ORDER)).thenReturn(pendingOrder());

        assertThrows(IllegalStateException.class, () -> paymentService.refund(SELLER, ORDER));

        verify(orderMapper, never()).transition(anyLong(), anyString(), anyString(), any());
        verifyNoInteractions(walletService);
    }

    @Test
    @DisplayName("A shipped order cannot be refunded; the state machine says so, not this class")
    void shippedOrderCannotBeRefunded() {
        Order shipped = pendingOrder();
        shipped.setStatus(OrderStatus.SHIPPED.name());
        when(orderMapper.selectById(ORDER)).thenReturn(shipped);

        assertThrows(IllegalStateException.class, () -> paymentService.refund(SELLER, ORDER));
        verifyNoInteractions(walletService);
    }

    @Test
    @DisplayName("Only the order's own seller can refund it")
    void otherSellerCannotRefund() {
        when(orderMapper.selectById(ORDER)).thenReturn(paidOrder());

        assertThrows(IllegalArgumentException.class, () -> paymentService.refund(999L, ORDER));
        verifyNoInteractions(walletService);
    }

    @Test
    @DisplayName("An external method's refund moves nothing on the platform: nothing was held here")
    void gatewayRefundMovesNothingOnPlatform() {
        PaymentService withGateway = withAlipay();
        stubRefundableOrder("alipay", "30.00");

        withGateway.refund(SELLER, ORDER);

        // The buyer is reimbursed off-platform (NFR 6.4), and the seller was
        // never credited, so neither balance changes here.
        verifyNoInteractions(walletService);
    }

    // ---- Sprint 4: the payout is what hands the money over (#109) ----

    private PaymentService withAlipay() {
        return new PaymentService(orderMapper, orderItemMapper, productMapper,
                transactionMapper, event -> { },
                List.of(new AlipayPaymentStrategy(walletService)));
    }

    private void stubPaidOrderWithLedger(String method, String amount) {
        lenient().when(transactionMapper.selectByOrderId(ORDER))
                .thenReturn(List.of(paymentRow(method, amount)));
    }

    @Test
    @DisplayName("Releasing to the seller credits them and records a payout beside the payment")
    void payoutCreditsTheSellerAndIsAppendOnly() {
        stubPaidOrderWithLedger("balance", "30.00");

        paymentService.releaseToSeller(paidOrder());

        verify(walletService).credit(SELLER, new BigDecimal("30.00"));
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionMapper).insert(captor.capture());
        Transaction recorded = captor.getValue();
        assertEquals(TransactionType.PAYOUT.name(), recorded.getTransactionType());
        assertEquals(new BigDecimal("30.00"), recorded.getAmount());
        assertEquals("balance", recorded.getPaymentMethod());
        assertEquals("SUCCESS", recorded.getStatus());
        // The payment row is read, never rewritten.
        verify(transactionMapper).selectByOrderId(ORDER);
        verifyNoMoreInteractions(transactionMapper);
    }

    @Test
    @DisplayName("The seller is paid what the buyer actually paid, not the order total")
    void payoutUsesTheAmountActuallyPaid() {
        // A bargain was accepted at 18.00 while the order total says 30.00.
        stubPaidOrderWithLedger("balance", "18.00");

        paymentService.releaseToSeller(paidOrder());

        verify(walletService).credit(SELLER, new BigDecimal("18.00"));
    }

    @Test
    @DisplayName("An external method pays the seller on the same signal, not at payment")
    void gatewayPayoutFollowsTheSameTiming() {
        PaymentService withGateway = withAlipay();
        stubPaidOrderWithLedger("alipay", "30.00");

        withGateway.releaseToSeller(paidOrder());

        verify(walletService).credit(SELLER, new BigDecimal("30.00"));
    }

    @Test
    @DisplayName("Paying through an external method credits nobody until receipt is confirmed")
    void gatewayPaymentCreditsNobodyYet() {
        PaymentService withGateway = withAlipay();
        stubPayableOrder();

        withGateway.pay(BUYER, ORDER, "alipay", "123456");

        verify(walletService).verifyPaymentPassword(BUYER, "123456");
        verify(walletService, never()).credit(anyLong(), any());
        verify(walletService, never()).pay(anyLong(), anyString(), any());
    }

    // ---- Sprint 4: a fifth payment method is a class, not a branch ----

    private PaymentService withPayNow() {
        return new PaymentService(orderMapper, orderItemMapper, productMapper,
                transactionMapper, event -> { },
                List.of(new PayNowPaymentStrategy(walletService)));
    }

    @Test
    @DisplayName("PayNow settles outside this system, like the other external methods")
    void payNowSettlesOffPlatform() {
        PaymentService withGateway = withPayNow();
        stubPayableOrder();

        withGateway.pay(BUYER, ORDER, "paynow", "123456");

        verify(walletService).verifyPaymentPassword(BUYER, "123456");
        verify(walletService, never()).pay(anyLong(), anyString(), any());
        verify(walletService, never()).credit(anyLong(), any());

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionMapper).insert(captor.capture());
        assertEquals("paynow", captor.getValue().getPaymentMethod());
    }

    @Test
    @DisplayName("A PayNow refund comes back as account balance: the transfer cannot be reversed")
    void payNowRefundLandsInTheBuyersBalance() {
        PaymentService withGateway = withPayNow();
        stubRefundableOrder("paynow", "30.00");

        withGateway.refund(SELLER, ORDER);

        verify(walletService).credit(BUYER, new BigDecimal("30.00"));
        // Nothing is taken from the seller here either; they were never paid.
        verify(walletService, never()).debit(anyLong(), any());
    }

    @Test
    @DisplayName("An order with no successful payment cannot be paid out")
    void payoutWithoutAPaymentIsRefused() {
        when(transactionMapper.selectByOrderId(ORDER)).thenReturn(List.of());

        assertThrows(IllegalStateException.class,
                () -> paymentService.releaseToSeller(paidOrder()));

        verifyNoInteractions(walletService);
        verify(transactionMapper, never()).insert(any());
    }

    // ---- What is still held is the difference between the ledger rows ----

    private Transaction row(TransactionType type, String amount, String status) {
        Transaction movement = new Transaction();
        movement.setOrderId(ORDER);
        movement.setAmount(new BigDecimal(amount));
        movement.setPaymentMethod("balance");
        movement.setTransactionType(type.name());
        movement.setStatus(status);
        movement.setCreatedAt(LocalDateTime.of(2026, 10, 17, 9, 0));
        return movement;
    }

    @Test
    @DisplayName("A paid order holds what was paid")
    void heldAmountIsThePaymentWhileNothingHasLeft() {
        when(transactionMapper.selectByOrderId(ORDER))
                .thenReturn(List.of(row(TransactionType.PAYMENT, "30.00", "SUCCESS")));

        assertEquals(new BigDecimal("30.00"), paymentService.heldAmount(ORDER));
    }

    @Test
    @DisplayName("A completed order holds nothing: the payout took it")
    void heldAmountIsZeroAfterThePayout() {
        when(transactionMapper.selectByOrderId(ORDER)).thenReturn(List.of(
                row(TransactionType.PAYMENT, "30.00", "SUCCESS"),
                row(TransactionType.PAYOUT, "30.00", "SUCCESS")));

        assertEquals(0, paymentService.heldAmount(ORDER).signum());
    }

    @Test
    @DisplayName("A refunded order holds nothing: the buyer took it back")
    void heldAmountIsZeroAfterARefund() {
        when(transactionMapper.selectByOrderId(ORDER)).thenReturn(List.of(
                row(TransactionType.PAYMENT, "30.00", "SUCCESS"),
                row(TransactionType.REFUND, "30.00", "SUCCESS")));

        assertEquals(0, paymentService.heldAmount(ORDER).signum());
    }

    @Test
    @DisplayName("A failed movement is not money: it changes nothing that is held")
    void heldAmountIgnoresFailedRows() {
        when(transactionMapper.selectByOrderId(ORDER)).thenReturn(List.of(
                row(TransactionType.PAYMENT, "30.00", "SUCCESS"),
                row(TransactionType.PAYOUT, "30.00", "FAILED")));

        assertEquals(new BigDecimal("30.00"), paymentService.heldAmount(ORDER));
    }

    @Test
    @DisplayName("An order that was never paid holds nothing")
    void heldAmountOfAnUnpaidOrderIsZero() {
        when(transactionMapper.selectByOrderId(ORDER)).thenReturn(List.of());

        assertEquals(0, paymentService.heldAmount(ORDER).signum());
    }
}

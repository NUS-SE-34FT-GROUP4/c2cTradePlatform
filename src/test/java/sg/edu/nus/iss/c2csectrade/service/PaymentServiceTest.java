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
import sg.edu.nus.iss.c2csectrade.service.payment.BalancePaymentStrategy;
import sg.edu.nus.iss.c2csectrade.service.payment.PaymentStrategy;

import java.math.BigDecimal;
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
    @DisplayName("Paying by balance debits the buyer, credits the seller and records the movement")
    void balancePaymentSettlesBothSides() {
        stubPayableOrder();

        paymentService.pay(BUYER, ORDER, "balance", "123456");

        verify(walletService).pay(BUYER, "123456", new BigDecimal("30.00"));
        verify(walletService).credit(SELLER, new BigDecimal("30.00"));

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
    @DisplayName("Insufficient balance leaves the seller uncredited and records nothing")
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
}

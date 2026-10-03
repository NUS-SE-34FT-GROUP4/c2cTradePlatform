package sg.edu.nus.iss.c2csectrade.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sg.edu.nus.iss.c2csectrade.entity.*;
import sg.edu.nus.iss.c2csectrade.mapper.OrderItemMapper;
import sg.edu.nus.iss.c2csectrade.mapper.OrderMapper;
import sg.edu.nus.iss.c2csectrade.mapper.ProductMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Sprint 3 acceptance checks for the order state machine. */
@ExtendWith(MockitoExtension.class)
class OrderStateServiceTest {

    @Mock private OrderMapper orderMapper;
    @Mock private OrderItemMapper orderItemMapper;
    @Mock private ProductMapper productMapper;
    @InjectMocks private OrderStateService orderStateService;

    private static final Long BUYER = 10L, SELLER = 20L, ORDER = 500L;

    private Order order(OrderStatus status) {
        Order o = new Order();
        o.setId(ORDER);
        o.setBuyerId(BUYER);
        o.setSellerId(SELLER);
        o.setStatus(status.name());
        return o;
    }

    private void stub(OrderStatus status) {
        when(orderMapper.selectById(ORDER)).thenReturn(order(status));
        lenient().when(orderMapper.transition(eq(ORDER), anyString(), anyString(), isNull()))
                .thenReturn(1);
    }

    @Test
    @DisplayName("The permitted path runs: paid to shipped to completed")
    void happyPath() {
        stub(OrderStatus.PAID);
        orderStateService.apply(SELLER, ORDER, OrderAction.SHIP);
        verify(orderMapper).transition(ORDER, "PAID", "SHIPPED", null);

        reset(orderMapper);
        stub(OrderStatus.SHIPPED);
        orderStateService.apply(BUYER, ORDER, OrderAction.CONFIRM_RECEIPT);
        verify(orderMapper).transition(ORDER, "SHIPPED", "COMPLETED", null);
    }

    @Test
    @DisplayName("An order cannot be shipped before it is paid")
    void cannotShipUnpaidOrder() {
        when(orderMapper.selectById(ORDER)).thenReturn(order(OrderStatus.PENDING_PAYMENT));
        assertThrows(IllegalStateException.class,
                () -> orderStateService.apply(SELLER, ORDER, OrderAction.SHIP));
        verify(orderMapper, never()).transition(anyLong(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("A completed order permits nothing further")
    void completedIsTerminal() {
        when(orderMapper.selectById(ORDER)).thenReturn(order(OrderStatus.COMPLETED));
        for (OrderAction action : OrderAction.values()) {
            assertThrows(IllegalStateException.class,
                    () -> orderStateService.apply(BUYER, ORDER, action));
        }
    }

    @Test
    @DisplayName("Only the seller may dispatch, only the buyer may confirm receipt")
    void wrongPartyIsRefused() {
        stub(OrderStatus.PAID);
        assertThrows(IllegalArgumentException.class,
                () -> orderStateService.apply(BUYER, ORDER, OrderAction.SHIP));

        reset(orderMapper);
        stub(OrderStatus.SHIPPED);
        assertThrows(IllegalArgumentException.class,
                () -> orderStateService.apply(SELLER, ORDER, OrderAction.CONFIRM_RECEIPT));
    }

    @Test
    @DisplayName("Two concurrent attempts at the same transition: one wins")
    void concurrentTransitionIsRefused() {
        when(orderMapper.selectById(ORDER)).thenReturn(order(OrderStatus.PAID));
        when(orderMapper.transition(eq(ORDER), anyString(), anyString(), isNull())).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> orderStateService.apply(SELLER, ORDER, OrderAction.SHIP));
    }

    @Test
    @DisplayName("Cancelling an unpaid order releases its reservation")
    void cancelReleasesReservation() {
        stub(OrderStatus.PENDING_PAYMENT);
        OrderItem item = new OrderItem();
        item.setProductId(101L);
        item.setQuantity(3);
        when(orderItemMapper.selectByOrderId(ORDER)).thenReturn(List.of(item));

        orderStateService.apply(BUYER, ORDER, OrderAction.CANCEL);

        verify(productMapper).releaseStock(101L, 3);
    }

    @Test
    @DisplayName("Completing a shipped order releases nothing: the stock was deducted at payment")
    void completionReleasesNothing() {
        stub(OrderStatus.SHIPPED);
        orderStateService.apply(BUYER, ORDER, OrderAction.CONFIRM_RECEIPT);
        verifyNoInteractions(productMapper);
    }

    @Test
    @DisplayName("Each state answers for itself which actions it permits")
    void statesDeclareTheirOwnRules() {
        assertTrue(OrderStatus.PENDING_PAYMENT.permits(OrderAction.PAY));
        assertTrue(OrderStatus.PENDING_PAYMENT.permits(OrderAction.EXPIRE));
        assertFalse(OrderStatus.PENDING_PAYMENT.permits(OrderAction.SHIP));
        assertTrue(OrderStatus.PAID.permits(OrderAction.SHIP));
        assertFalse(OrderStatus.PAID.permits(OrderAction.CANCEL));
        assertFalse(OrderStatus.EXPIRED.permits(OrderAction.PAY));
    }
}

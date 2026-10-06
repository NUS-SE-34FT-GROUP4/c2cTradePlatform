package sg.edu.nus.iss.c2csectrade.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sg.edu.nus.iss.c2csectrade.service.NotificationService;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The Observer: one event reaches both parties of the order. */
@ExtendWith(MockitoExtension.class)
class OrderNotificationListenerTest {

    @Mock private NotificationService notifications;

    @InjectMocks private OrderNotificationListener listener;

    private OrderStateChangedEvent event() {
        return new OrderStateChangedEvent(500L, "20261001120000001", 10L, 20L,
                "PENDING_PAYMENT", "PAID", new BigDecimal("30.00"));
    }

    @Test
    @DisplayName("A state change notifies the buyer and the seller once each")
    void notifiesBothParties() {
        listener.on(event());

        verify(notifications, times(1)).deliver(eq(10L), eq(500L), contains("PAID"));
        verify(notifications, times(1)).deliver(eq(20L), eq(500L), contains("PAID"));
        verify(notifications, times(2)).deliver(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("The message carries the order number and the new state")
    void messageNamesTheOrderAndState() {
        listener.on(event());

        verify(notifications).deliver(eq(10L), eq(500L),
                eq("Order 20261001120000001 (30.00) is now PAID."));
    }
}

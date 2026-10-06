package sg.edu.nus.iss.c2csectrade.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import sg.edu.nus.iss.c2csectrade.entity.SystemNotification;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.mapper.SystemNotificationMapper;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Delivery guarantees: one row per user, one push per user, and a failing
 * notification never propagates — the order flow that raised it must not care.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private SystemNotificationMapper notificationMapper;
    @Mock private UserMapper userMapper;
    @Mock private SimpMessagingTemplate messagingTemplate;

    private NotificationService notificationService;

    private static final Long USER_ID = 10L;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationMapper, userMapper, messagingTemplate);
    }

    private User user() {
        User user = new User();
        user.setId(USER_ID);
        user.setUsername("buyer_a");
        return user;
    }

    @Test
    @DisplayName("Delivering persists a row and pushes to the user's own queue")
    void persistsThenPushes() {
        when(userMapper.selectById(USER_ID)).thenReturn(user());

        notificationService.deliver(USER_ID, 500L, "Order A is now PAID.");

        ArgumentCaptor<SystemNotification> captor = ArgumentCaptor.forClass(SystemNotification.class);
        verify(notificationMapper).insert(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(USER_ID, captor.getValue().getUserId());
        org.junit.jupiter.api.Assertions.assertEquals(Long.valueOf(500L), captor.getValue().getOrderId());

        verify(messagingTemplate).convertAndSendToUser(eq("buyer_a"), eq("/queue/system"), any());
    }

    @Test
    @DisplayName("A failing WebSocket push is swallowed, the row stays persisted")
    void pushFailureDoesNotThrow() {
        when(userMapper.selectById(USER_ID)).thenReturn(user());
        doThrow(new RuntimeException("broker down"))
                .when(messagingTemplate).convertAndSendToUser(anyString(), anyString(), any());

        assertDoesNotThrow(() ->
                notificationService.deliver(USER_ID, 500L, "Order A is now PAID."));
        verify(notificationMapper).insert(any(SystemNotification.class));
    }

    @Test
    @DisplayName("A failing write is swallowed too — notification never breaks the order flow")
    void persistFailureDoesNotThrow() {
        when(notificationMapper.insert(any(SystemNotification.class)))
                .thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(() ->
                notificationService.deliver(USER_ID, 500L, "Order A is now PAID."));
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Marking read with an empty id list writes nothing")
    void markReadWithNoIdsIsNoop() {
        notificationService.markRead(USER_ID, List.of());
        verify(notificationMapper, never()).markRead(anyLong(), anyList());
    }
}

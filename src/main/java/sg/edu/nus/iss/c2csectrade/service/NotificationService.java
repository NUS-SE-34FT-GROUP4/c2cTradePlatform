package sg.edu.nus.iss.c2csectrade.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import sg.edu.nus.iss.c2csectrade.entity.SystemNotification;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.mapper.SystemNotificationMapper;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persisting and delivering system notifications.
 *
 * {@link #deliver} is the method the order-state Observer calls. It is
 * deliberately defensive: persistence and the WebSocket push are wrapped
 * together, and any failure is logged and swallowed, because a notification
 * must never break the order flow that raised it.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final SystemNotificationMapper notificationMapper;
    private final UserMapper userMapper;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(SystemNotificationMapper notificationMapper,
                               UserMapper userMapper,
                               SimpMessagingTemplate messagingTemplate) {
        this.notificationMapper = notificationMapper;
        this.userMapper = userMapper;
        this.messagingTemplate = messagingTemplate;
    }

    /** Persist one notification for the user and push it live if they are on. */
    public void deliver(Long userId, Long orderId, String content) {
        try {
            SystemNotification notification = new SystemNotification();
            notification.setUserId(userId);
            notification.setOrderId(orderId);
            notification.setContent(content);
            notificationMapper.insert(notification);

            User user = userMapper.selectById(userId);
            if (user != null) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("isSystemMessage", true);
                payload.put("recipient", user.getUsername());
                payload.put("content", content);
                payload.put("timestamp", Instant.now().toString());
                // Per-user queue, same delivery style as chat.
                messagingTemplate.convertAndSendToUser(user.getUsername(), "/queue/system", payload);
            }
        } catch (Exception e) {
            log.warn("System notification for user {} could not be delivered: {}", userId, e.getMessage());
        }
    }

    /**
     * History for the caller. Shaped to the contract the notification toasts
     * in the frontend already read: isSystemMessage / isRead / timestamp.
     */
    public List<Map<String, Object>> history(Long userId, int limit) {
        return notificationMapper.selectForUser(userId, limit).stream()
                .map(n -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", n.getId());
                    item.put("orderId", n.getOrderId());
                    item.put("content", n.getContent());
                    item.put("isSystemMessage", true);
                    item.put("isRead", n.getReadFlag() != null && n.getReadFlag() == 1);
                    item.put("timestamp", n.getCreatedAt() == null
                            ? Instant.now().toString() : n.getCreatedAt().toString());
                    return item;
                })
                .toList();
    }

    public int markRead(Long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return notificationMapper.markRead(userId, ids);
    }
}

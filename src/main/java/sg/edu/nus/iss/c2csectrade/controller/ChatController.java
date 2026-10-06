package sg.edu.nus.iss.c2csectrade.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.iss.c2csectrade.entity.ChatMessage;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;
import sg.edu.nus.iss.c2csectrade.service.ChatService;
import sg.edu.nus.iss.c2csectrade.service.NotificationService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final UserMapper userMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;

    public ChatController(ChatService chatService,
                          UserMapper userMapper,
                          SimpMessagingTemplate messagingTemplate,
                          NotificationService notificationService) {
        this.chatService = chatService;
        this.userMapper = userMapper;
        this.messagingTemplate = messagingTemplate;
        this.notificationService = notificationService;
    }

    @PostMapping("/messages")
    public ResponseEntity<?> send(@RequestBody Map<String, Object> body, Authentication authentication) {
        User sender = currentUser(authentication);
        if (sender == null) {
            return unauthenticated();
        }
        try {
            Long receiverId = Long.valueOf(String.valueOf(body.get("receiverId")));
            Long productId = body.get("productId") == null
                    ? null : Long.valueOf(String.valueOf(body.get("productId")));
            ChatMessage saved = chatService.send(sender.getId(), receiverId, productId,
                    String.valueOf(body.get("content")));

            User receiver = userMapper.selectById(receiverId);
            if (receiver != null) {
                // Push to the recipient's own queue; delivery is per user, not broadcast.
                messagingTemplate.convertAndSendToUser(receiver.getUsername(), "/queue/messages", saved);
            }
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** History with one other participant. The caller is always one end of it. */
    @GetMapping("/messages")
    public ResponseEntity<?> history(@RequestParam Long withUserId,
                                     @RequestParam(required = false) Long productId,
                                     Authentication authentication) {
        User caller = currentUser(authentication);
        if (caller == null) {
            return unauthenticated();
        }
        return ResponseEntity.ok(chatService.history(caller.getId(), withUserId, productId));
    }

    @GetMapping("/conversations")
    public ResponseEntity<?> conversations(Authentication authentication) {
        User caller = currentUser(authentication);
        if (caller == null) {
            return unauthenticated();
        }
        return ResponseEntity.ok(Map.of(
                "conversations", chatService.conversations(caller.getId()),
                "unread", chatService.unreadCount(caller.getId())));
    }

    /**
     * Persisted system notifications for the caller, newest first. The toast
     * scaffolding in App.vue reads this on mount, which is how someone who was
     * offline when the order moved still finds out.
     */
    @GetMapping("/history/system")
    public ResponseEntity<?> systemHistory(@RequestParam(defaultValue = "10") int limit,
                                           Authentication authentication) {
        User caller = currentUser(authentication);
        if (caller == null) {
            return unauthenticated();
        }
        List<Map<String, Object>> items = notificationService.history(caller.getId(), limit);
        return ResponseEntity.ok(items);
    }

    /** Mark the given system notifications as read for the caller. */
    @PostMapping("/history/system/read")
    public ResponseEntity<?> markSystemRead(@RequestBody Map<String, Object> body,
                                            Authentication authentication) {
        User caller = currentUser(authentication);
        if (caller == null) {
            return unauthenticated();
        }
        @SuppressWarnings("unchecked")
        List<Number> raw = (List<Number>) body.get("ids");
        List<Long> ids = raw == null ? List.of()
                : raw.stream().map(Number::longValue).toList();
        notificationService.markRead(caller.getId(), ids);
        return ResponseEntity.ok(Map.of("marked", ids.size()));
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userMapper.selectByUsername(authentication.getName());
    }

    private ResponseEntity<?> unauthenticated() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Not authenticated"));
    }
}

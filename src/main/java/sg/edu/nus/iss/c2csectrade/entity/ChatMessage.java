package sg.edu.nus.iss.c2csectrade.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ChatMessage implements Serializable {
    private Long id;
    /** Deterministic key for the pair, so both directions land in one thread. */
    private String conversationId;
    private Long senderId;
    private Long receiverId;
    private Long productId;
    private String content;
    private Integer readFlag;
    private LocalDateTime createdAt;

    private String senderName;
}

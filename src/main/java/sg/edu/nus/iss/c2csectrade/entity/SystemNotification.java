package sg.edu.nus.iss.c2csectrade.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * A persisted notification for one user. Written when an order state changes,
 * read back by the notification list, so a user who was offline at the time
 * still sees what happened.
 */
@Data
public class SystemNotification implements Serializable {
    private Long id;
    private Long userId;
    private Long orderId;
    private String content;
    private Integer readFlag;
    private LocalDateTime createdAt;
}

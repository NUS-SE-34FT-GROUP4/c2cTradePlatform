package sg.edu.nus.iss.c2csectrade.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** A user's report on a product or another user, and the administrator's decision on it. */
@Data
public class Report implements Serializable {
    public static final String TARGET_PRODUCT = "PRODUCT";
    public static final String TARGET_USER = "USER";
    public static final String OPEN = "OPEN";
    public static final String UPHELD = "UPHELD";
    public static final String DISMISSED = "DISMISSED";

    private Long id;
    private Long reporterId;
    private String targetType;
    private Long targetId;
    private String reason;
    private String detail;
    private String status;
    private Long decidedBy;
    private String decisionNote;
    private LocalDateTime createdAt;
    private LocalDateTime decidedAt;
}

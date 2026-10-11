package sg.edu.nus.iss.c2csectrade.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** One administrator action; rows are only ever inserted. */
@Data
public class AdminAuditLog implements Serializable {
    private Long id;
    private Long adminId;
    private String action;
    private String targetType;
    private Long targetId;
    private Long reportId;
    private String note;
    private LocalDateTime createdAt;
}

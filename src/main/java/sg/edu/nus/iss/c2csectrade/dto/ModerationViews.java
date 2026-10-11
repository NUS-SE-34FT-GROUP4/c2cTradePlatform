package sg.edu.nus.iss.c2csectrade.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/** Response shapes for the moderation API, as specified in docs/api/moderation.md §4. */
public final class ModerationViews {
    private ModerationViews() {
    }

    public record UserRef(Long id, String username) {
    }

    public record ReportView(Long id, UserRef reporter, String targetType, Object target, String reason,
                             String reasonLabel, String detail, String status, UserRef decidedBy,
                             String decisionNote, LocalDateTime createdAt, LocalDateTime decidedAt) {
    }

    public record ProductTarget(Long id, String name, Long ownerId, String ownerUsername, int status) {
    }

    public record UserTarget(Long id, String username, String displayName, String status) {
    }

    public record AdminUserView(Long id, String username, String displayName, String email, List<String> roles,
                                String status, String banReason, Instant bannedAt, Instant createdAt,
                                int openReports) {
    }

    public record AuditLogView(Long id, UserRef admin, String action, String targetType, Long targetId,
                               Long reportId, String note, LocalDateTime createdAt) {
    }

    public record ReasonView(String code, String label) {
    }

    public record PageView<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <T> PageView<T> of(List<T> content, int page, int size, long total) {
            return new PageView<>(content, page, size, total, (int) ((total + size - 1) / size));
        }
    }
}

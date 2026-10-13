package sg.edu.nus.iss.c2csectrade.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.iss.c2csectrade.dto.ModerationViews.*;
import sg.edu.nus.iss.c2csectrade.entity.*;
import sg.edu.nus.iss.c2csectrade.exception.ModerationException;
import sg.edu.nus.iss.c2csectrade.mapper.AdminAuditLogMapper;
import sg.edu.nus.iss.c2csectrade.mapper.ProductMapper;
import sg.edu.nus.iss.c2csectrade.mapper.ReportMapper;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Reports, bans and the admin audit log (#61, #63). The contract is
 * docs/api/moderation.md; every administrator action writes its audit row in
 * the same transaction as the change itself.
 */
@Service
public class ModerationService {
    public static final String ACTIVE = "ACTIVE";
    public static final String BANNED = "BANNED";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final int DELISTED = 0;

    private final ReportMapper reports;
    private final AdminAuditLogMapper auditLog;
    private final UserMapper users;
    private final ProductMapper products;

    public ModerationService(ReportMapper reports, AdminAuditLogMapper auditLog, UserMapper users,
                             ProductMapper products) {
        this.reports = reports;
        this.auditLog = auditLog;
        this.users = users;
        this.products = products;
    }

    // ------------------------------------------------------------ users (§2)

    public List<ReasonView> reasons() {
        return Arrays.stream(ReportReason.values()).map(r -> new ReasonView(r.name(), r.getLabel())).toList();
    }

    @Transactional
    public ReportView submit(String reporterUsername, String targetType, Long targetId, String reason, String detail) {
        User reporter = requireUser(reporterUsername);
        if (!Report.TARGET_PRODUCT.equals(targetType) && !Report.TARGET_USER.equals(targetType)) {
            throw ModerationException.badRequest("targetType must be PRODUCT or USER");
        }
        if (targetId == null) {
            throw ModerationException.badRequest("targetId is required");
        }
        ReportReason.parse(reason).orElseThrow(() -> ModerationException.badRequest("Unknown reason: " + reason));
        if (detail != null && detail.length() > 1000) {
            throw ModerationException.badRequest("detail must be at most 1000 characters");
        }
        Long ownerId = Report.TARGET_PRODUCT.equals(targetType) ? productOwner(targetId) : userExists(targetId);
        if (reporter.getId().equals(ownerId)) {
            throw ModerationException.badRequest("You cannot report yourself or your own listing");
        }
        if (reports.countOpenByReporterAndTarget(reporter.getId(), targetType, targetId) > 0) {
            throw ModerationException.conflict("You already have an open report on this " + targetType.toLowerCase());
        }
        Report report = new Report();
        report.setReporterId(reporter.getId());
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        report.setReason(reason);
        report.setDetail(detail == null || detail.isBlank() ? null : detail.trim());
        reports.insert(report);
        return view(reports.selectById(report.getId()));
    }

    public PageView<ReportView> mine(String username, int page, int size) {
        User reporter = requireUser(username);
        List<ReportView> content = reports.selectByReporter(reporter.getId(), size, (long) page * size)
                .stream().map(this::view).toList();
        return PageView.of(content, page, size, reports.countByReporter(reporter.getId()));
    }

    // ------------------------------------------------------------ report queue (§3.1)

    public PageView<ReportView> queue(String status, String targetType, int page, int size) {
        String s = status == null || status.isBlank() ? Report.OPEN : status;
        if (!List.of(Report.OPEN, Report.UPHELD, Report.DISMISSED).contains(s)) {
            throw ModerationException.badRequest("status must be OPEN, UPHELD or DISMISSED");
        }
        String t = targetType == null || targetType.isBlank() ? null : targetType;
        List<ReportView> content = reports.selectForQueue(s, t, size, (long) page * size)
                .stream().map(this::view).toList();
        return PageView.of(content, page, size, reports.countForQueue(s, t));
    }

    @Transactional
    public ReportView uphold(String adminUsername, Long reportId, String note) {
        User admin = requireUser(adminUsername);
        Report report = openReport(reportId);
        String trimmed = trimNote(note);
        if (Report.TARGET_USER.equals(report.getTargetType())) {
            User target = users.selectById(report.getTargetId());
            if (target != null && !BANNED.equals(target.getStatus())) {
                // Same rules as a direct ban: refuse before anything changes, so the report stays OPEN
                // and can be dismissed instead.
                checkBannable(admin, target);
                String label = ReportReason.parse(report.getReason()).map(ReportReason::getLabel).orElse(report.getReason());
                applyBan(admin, target, trimmed == null ? label : label + ": " + trimmed, report.getId());
            }
        } else if (products.selectById(report.getTargetId()) != null) {
            products.updateStatus(report.getTargetId(), DELISTED);
            audit(admin, "DELIST_PRODUCT", Report.TARGET_PRODUCT, report.getTargetId(), report.getId(), trimmed);
        }
        // Every open report on the same target is now handled, not just this one.
        reports.decideAllOpenOnTarget(report.getTargetType(), report.getTargetId(), Report.UPHELD, admin.getId(), trimmed);
        audit(admin, "UPHOLD_REPORT", report.getTargetType(), report.getTargetId(), report.getId(), trimmed);
        return view(reports.selectById(reportId));
    }

    @Transactional
    public ReportView dismiss(String adminUsername, Long reportId, String note) {
        User admin = requireUser(adminUsername);
        Report report = openReport(reportId);
        String trimmed = trimNote(note);
        reports.decide(report.getId(), Report.DISMISSED, admin.getId(), trimmed);
        audit(admin, "DISMISS_REPORT", report.getTargetType(), report.getTargetId(), report.getId(), trimmed);
        return view(reports.selectById(reportId));
    }

    // ------------------------------------------------------------ users (§3.2)

    public PageView<AdminUserView> searchUsers(String status, String q, int page, int size) {
        String s = status == null || status.isBlank() ? null : status;
        String query = q == null || q.isBlank() ? null : q.trim();
        List<AdminUserView> content = users.searchForAdmin(s, query, size, (long) page * size)
                .stream().map(this::adminView).toList();
        return PageView.of(content, page, size, users.countForAdmin(s, query));
    }

    @Transactional
    public AdminUserView ban(String adminUsername, Long userId, String reason) {
        User admin = requireUser(adminUsername);
        if (reason == null || reason.isBlank()) {
            throw ModerationException.badRequest("A reason is required; it is shown to the user");
        }
        if (reason.trim().length() > 500) {
            throw ModerationException.badRequest("reason must be at most 500 characters");
        }
        User target = users.selectById(userId);
        if (target == null) {
            throw ModerationException.notFound("User " + userId + " does not exist");
        }
        checkBannable(admin, target);
        if (BANNED.equals(target.getStatus())) {
            throw ModerationException.conflict(target.getUsername() + " is already banned");
        }
        applyBan(admin, target, reason.trim(), null);
        return adminView(users.selectById(userId));
    }

    @Transactional
    public AdminUserView reinstate(String adminUsername, Long userId, String note) {
        User admin = requireUser(adminUsername);
        User target = users.selectById(userId);
        if (target == null) {
            throw ModerationException.notFound("User " + userId + " does not exist");
        }
        if (!BANNED.equals(target.getStatus())) {
            throw ModerationException.conflict(target.getUsername() + " is not banned");
        }
        // A ban only blocks requests and deletes nothing, so there is nothing else to restore.
        users.updateBan(userId, ACTIVE, null, null);
        audit(admin, "REINSTATE_USER", Report.TARGET_USER, userId, null, trimNote(note));
        return adminView(users.selectById(userId));
    }

    // ------------------------------------------------------------ audit log (§3.3)

    public PageView<AuditLogView> auditLog(String action, int page, int size) {
        String a = action == null || action.isBlank() ? null : action;
        List<AuditLogView> content = auditLog.select(a, size, (long) page * size).stream()
                .map(e -> new AuditLogView(e.getId(), ref(e.getAdminId()), e.getAction(), e.getTargetType(),
                        e.getTargetId(), e.getReportId(), e.getNote(), e.getCreatedAt()))
                .toList();
        return PageView.of(content, page, size, auditLog.count(a));
    }

    // ------------------------------------------------------------ helpers

    private void checkBannable(User admin, User target) {
        if (target.getId().equals(admin.getId())) {
            throw ModerationException.badRequest("You cannot ban yourself");
        }
        if (isAdmin(target)) {
            throw ModerationException.badRequest("Administrators cannot be banned");
        }
    }

    private void applyBan(User admin, User target, String reason, Long reportId) {
        users.updateBan(target.getId(), BANNED, reason, Instant.now());
        audit(admin, "BAN_USER", Report.TARGET_USER, target.getId(), reportId, reason);
    }

    private void audit(User admin, String action, String targetType, Long targetId, Long reportId, String note) {
        AdminAuditLog entry = new AdminAuditLog();
        entry.setAdminId(admin.getId());
        entry.setAction(action);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        entry.setReportId(reportId);
        entry.setNote(note);
        auditLog.insert(entry);
    }

    private Report openReport(Long reportId) {
        Report report = reports.selectByIdForUpdate(reportId);
        if (report == null) {
            throw ModerationException.notFound("Report " + reportId + " does not exist");
        }
        if (!Report.OPEN.equals(report.getStatus())) {
            throw ModerationException.conflict("Report " + reportId + " has already been " + report.getStatus().toLowerCase());
        }
        return report;
    }

    private String trimNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        if (note.trim().length() > 500) {
            throw ModerationException.badRequest("note must be at most 500 characters");
        }
        return note.trim();
    }

    private User requireUser(String username) {
        User user = users.selectByUsername(username);
        if (user == null) {
            throw ModerationException.notFound("User " + username + " does not exist");
        }
        return user;
    }

    private Long productOwner(Long productId) {
        Product product = products.selectById(productId);
        if (product == null) {
            throw ModerationException.notFound("Product " + productId + " does not exist");
        }
        return product.getUserId();
    }

    private Long userExists(Long userId) {
        if (users.selectById(userId) == null) {
            throw ModerationException.notFound("User " + userId + " does not exist");
        }
        return userId;
    }

    private boolean isAdmin(User user) {
        User withRoles = users.selectByUsernameWithRoles(user.getUsername());
        return withRoles.getRoles() != null
                && withRoles.getRoles().stream().anyMatch(r -> ROLE_ADMIN.equals(r.getName()));
    }

    private UserRef ref(Long userId) {
        if (userId == null) {
            return null;
        }
        User user = users.selectById(userId);
        return new UserRef(userId, user == null ? null : user.getUsername());
    }

    private ReportView view(Report r) {
        Object target;
        if (Report.TARGET_PRODUCT.equals(r.getTargetType())) {
            Product p = products.selectById(r.getTargetId());
            target = p == null ? null : new ProductTarget(p.getId(), p.getName(), p.getUserId(),
                    ref(p.getUserId()).username(), p.getStatus());
        } else {
            User u = users.selectById(r.getTargetId());
            target = u == null ? null : new UserTarget(u.getId(), u.getUsername(), u.getDisplayName(), u.getStatus());
        }
        String label = ReportReason.parse(r.getReason()).map(ReportReason::getLabel).orElse(r.getReason());
        return new ReportView(r.getId(), ref(r.getReporterId()), r.getTargetType(), target, r.getReason(), label,
                r.getDetail(), r.getStatus(), ref(r.getDecidedBy()), r.getDecisionNote(), r.getCreatedAt(),
                r.getDecidedAt());
    }

    private AdminUserView adminView(User u) {
        List<String> roles = u.getRoles() == null
                ? users.selectByUsernameWithRoles(u.getUsername()).getRoles().stream().map(Role::getName).toList()
                : u.getRoles().stream().map(Role::getName).toList();
        return new AdminUserView(u.getId(), u.getUsername(), u.getDisplayName(), u.getEmail(), roles,
                Objects.requireNonNullElse(u.getStatus(), ACTIVE), u.getBanReason(), u.getBannedAt(),
                u.getCreatedAt(), reports.countOpenAgainstUser(u.getId()));
    }
}

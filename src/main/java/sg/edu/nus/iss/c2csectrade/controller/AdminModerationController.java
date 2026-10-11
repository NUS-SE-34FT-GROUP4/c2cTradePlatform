package sg.edu.nus.iss.c2csectrade.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.iss.c2csectrade.dto.ModerationViews.AdminUserView;
import sg.edu.nus.iss.c2csectrade.dto.ModerationViews.AuditLogView;
import sg.edu.nus.iss.c2csectrade.dto.ModerationViews.PageView;
import sg.edu.nus.iss.c2csectrade.dto.ModerationViews.ReportView;
import sg.edu.nus.iss.c2csectrade.service.ModerationService;

import static sg.edu.nus.iss.c2csectrade.controller.ReportController.checkPaging;

/**
 * Admin back office (#61): report queue, ban and reinstate, audit log.
 * /api/admin/** is limited to ROLE_ADMIN in SecurityConfig. See docs/api/moderation.md §3.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminModerationController {
    private final ModerationService moderation;

    public AdminModerationController(ModerationService moderation) {
        this.moderation = moderation;
    }

    public record NoteRequest(String note) {
    }

    public record BanRequest(String reason) {
    }

    @GetMapping("/reports")
    public PageView<ReportView> reports(@RequestParam(required = false) String status,
                                        @RequestParam(required = false) String targetType,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        checkPaging(page, size);
        return moderation.queue(status, targetType, page, size);
    }

    @PostMapping("/reports/{id}/uphold")
    public ReportView uphold(@PathVariable Long id, @RequestBody(required = false) NoteRequest body,
                             Authentication authentication) {
        return moderation.uphold(authentication.getName(), id, body == null ? null : body.note());
    }

    @PostMapping("/reports/{id}/dismiss")
    public ReportView dismiss(@PathVariable Long id, @RequestBody(required = false) NoteRequest body,
                              Authentication authentication) {
        return moderation.dismiss(authentication.getName(), id, body == null ? null : body.note());
    }

    @GetMapping("/users")
    public PageView<AdminUserView> users(@RequestParam(required = false) String status,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        checkPaging(page, size);
        return moderation.searchUsers(status, q, page, size);
    }

    @PostMapping("/users/{id}/ban")
    public AdminUserView ban(@PathVariable Long id, @RequestBody(required = false) BanRequest body,
                             Authentication authentication) {
        return moderation.ban(authentication.getName(), id, body == null ? null : body.reason());
    }

    @PostMapping("/users/{id}/reinstate")
    public AdminUserView reinstate(@PathVariable Long id, @RequestBody(required = false) NoteRequest body,
                                   Authentication authentication) {
        return moderation.reinstate(authentication.getName(), id, body == null ? null : body.note());
    }

    @GetMapping("/audit-log")
    public PageView<AuditLogView> auditLog(@RequestParam(required = false) String action,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        checkPaging(page, size);
        return moderation.auditLog(action, page, size);
    }
}

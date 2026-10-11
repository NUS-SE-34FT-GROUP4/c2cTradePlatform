package sg.edu.nus.iss.c2csectrade.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.iss.c2csectrade.dto.ModerationViews.PageView;
import sg.edu.nus.iss.c2csectrade.dto.ModerationViews.ReasonView;
import sg.edu.nus.iss.c2csectrade.dto.ModerationViews.ReportView;
import sg.edu.nus.iss.c2csectrade.exception.ModerationException;
import sg.edu.nus.iss.c2csectrade.service.ModerationService;

import java.util.List;

/** Submitting a report on a product or a user (#63); see docs/api/moderation.md §2. */
@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ModerationService moderation;

    public ReportController(ModerationService moderation) {
        this.moderation = moderation;
    }

    public record SubmitReportRequest(String targetType, Long targetId, String reason, String detail) {
    }

    @PostMapping
    public ResponseEntity<ReportView> submit(@RequestBody SubmitReportRequest request, Authentication authentication) {
        ReportView report = moderation.submit(authentication.getName(), request.targetType(), request.targetId(),
                request.reason(), request.detail());
        return ResponseEntity.status(HttpStatus.CREATED).body(report);
    }

    @GetMapping("/mine")
    public PageView<ReportView> mine(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size,
                                     Authentication authentication) {
        checkPaging(page, size);
        return moderation.mine(authentication.getName(), page, size);
    }

    @GetMapping("/reasons")
    public List<ReasonView> reasons() {
        return moderation.reasons();
    }

    static void checkPaging(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw ModerationException.badRequest("page must be >= 0 and size between 1 and 100");
        }
    }
}

package sg.edu.nus.iss.c2csectrade.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;
import sg.edu.nus.iss.c2csectrade.service.RecommendationService;

import java.util.Map;

@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final RecommendationService recommendationService;
    private final UserMapper userMapper;

    public HistoryController(RecommendationService recommendationService, UserMapper userMapper) {
        this.recommendationService = recommendationService;
        this.userMapper = userMapper;
    }

    /**
     * Records that the caller looked at a listing. The frontend fires this and
     * ignores the outcome, so it always answers 200: a failed view record must
     * never surface as an error on the product page.
     */
    @PostMapping("/view")
    public ResponseEntity<?> recordView(@RequestBody Map<String, Object> body, Authentication authentication) {
        if (authentication != null && authentication.getName() != null) {
            User user = userMapper.selectByUsername(authentication.getName());
            if (user != null && body.get("productId") != null) {
                recommendationService.recordView(user.getId(),
                        Long.valueOf(String.valueOf(body.get("productId"))));
            }
        }
        return ResponseEntity.ok(Map.of("recorded", true));
    }
}

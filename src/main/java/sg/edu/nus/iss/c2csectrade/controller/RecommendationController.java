package sg.edu.nus.iss.c2csectrade.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;
import sg.edu.nus.iss.c2csectrade.service.ProductService;
import sg.edu.nus.iss.c2csectrade.service.RecommendationService;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final ProductService productService;
    private final UserMapper userMapper;

    public RecommendationController(RecommendationService recommendationService,
                                    ProductService productService,
                                    UserMapper userMapper) {
        this.recommendationService = recommendationService;
        this.productService = productService;
        this.userMapper = userMapper;
    }

    /** Related items on the detail page. Open to anonymous visitors. */
    @GetMapping("/products/{productId}/similar")
    public ResponseEntity<?> similar(@PathVariable Long productId,
                                     @RequestParam(defaultValue = "8") int limit) {
        return ResponseEntity.ok(productService.convertToDTOList(
                recommendationService.similarTo(productId, Math.min(limit, 24))));
    }

    /** The home feed's suggestions; falls back to recent listings without history. */
    @GetMapping("/for-you")
    public ResponseEntity<?> forYou(@RequestParam(defaultValue = "8") int limit,
                                    Authentication authentication) {
        return ResponseEntity.ok(productService.convertToDTOList(
                recommendationService.forUser(currentUserId(authentication), Math.min(limit, 24))));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        User user = userMapper.selectByUsername(authentication.getName());
        return user == null ? null : user.getId();
    }
}

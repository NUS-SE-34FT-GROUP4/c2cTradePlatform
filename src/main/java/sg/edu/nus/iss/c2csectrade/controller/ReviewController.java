package sg.edu.nus.iss.c2csectrade.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import sg.edu.nus.iss.c2csectrade.dto.ReviewRequest;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;
import sg.edu.nus.iss.c2csectrade.service.FileStorageService;
import sg.edu.nus.iss.c2csectrade.service.ReviewService;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {
    private final ReviewService reviews;
    private final UserMapper users;
    private final FileStorageService storage;

    public ReviewController(ReviewService reviews, UserMapper users, FileStorageService storage) {
        this.reviews = reviews;
        this.users = users;
        this.storage = storage;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody ReviewRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("success", true, "data", reviews.createReview(userId(authentication), request)));
    }

    @GetMapping("/check/{orderId}")
    public Map<String, Object> check(@PathVariable Long orderId, Authentication authentication) {
        return Map.of("hasReviewed", reviews.hasReviewed(userId(authentication), orderId));
    }

    @GetMapping("/product/{productId}")
    public Map<String, Object> product(@PathVariable Long productId) {
        var list = reviews.getProductReviews(productId);
        double average = list.stream().mapToInt(r -> r.getProductRating()).average().orElse(0);
        return Map.of("reviews", list, "totalReviews", list.size(), "averageRating", average);
    }

    @PostMapping("/images")
    public ResponseEntity<?> image(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty() || file.getSize() > 5L * 1024 * 1024
                || file.getContentType() == null
                || !Set.of("image/jpeg", "image/png", "image/gif", "image/webp").contains(file.getContentType())) {
            throw new IllegalArgumentException("Upload a JPG, PNG, GIF or WebP image of at most 5MB");
        }
        return ResponseEntity.ok(Map.of("url", storage.upload(file, "review")));
    }

    private Long userId(Authentication authentication) {
        User user = authentication == null ? null : users.selectByUsername(authentication.getName());
        if (user == null) throw new org.springframework.security.access.AccessDeniedException("Not authenticated");
        return user.getId();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<?> invalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<?> conflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
    }
}

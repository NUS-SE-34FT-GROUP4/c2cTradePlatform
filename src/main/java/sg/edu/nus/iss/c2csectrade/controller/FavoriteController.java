package sg.edu.nus.iss.c2csectrade.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;
import sg.edu.nus.iss.c2csectrade.service.FavoriteService;

import java.util.Map;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final UserMapper userMapper;

    public FavoriteController(FavoriteService favoriteService, UserMapper userMapper) {
        this.favoriteService = favoriteService;
        this.userMapper = userMapper;
    }

    @PostMapping("/{productId}")
    public ResponseEntity<?> add(@PathVariable Long productId, Authentication authentication) {
        Long userId = currentUserId(authentication);
        if (userId == null) {
            return unauthenticated();
        }
        favoriteService.add(userId, productId);
        return ResponseEntity.ok(Map.of("favorited", true));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<?> remove(@PathVariable Long productId, Authentication authentication) {
        Long userId = currentUserId(authentication);
        if (userId == null) {
            return unauthenticated();
        }
        favoriteService.remove(userId, productId);
        return ResponseEntity.ok(Map.of("favorited", false));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<?> check(@PathVariable Long productId, Authentication authentication) {
        Long userId = currentUserId(authentication);
        if (userId == null) {
            return unauthenticated();
        }
        return ResponseEntity.ok(Map.of("favorited", favoriteService.isFavorited(userId, productId)));
    }

    @GetMapping
    public ResponseEntity<?> list(Authentication authentication) {
        Long userId = currentUserId(authentication);
        if (userId == null) {
            return unauthenticated();
        }
        return ResponseEntity.ok(Map.of(
                "items", favoriteService.list(userId),
                "count", favoriteService.count(userId)));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        User user = userMapper.selectByUsername(authentication.getName());
        return user == null ? null : user.getId();
    }

    private ResponseEntity<?> unauthenticated() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Not authenticated"));
    }
}

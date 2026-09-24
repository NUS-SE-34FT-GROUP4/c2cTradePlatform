package sg.edu.nus.iss.c2csectrade.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.exception.InsufficientStockException;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;
import sg.edu.nus.iss.c2csectrade.service.CartService;

import java.util.Map;
import java.util.function.Function;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;
    private final UserMapper userMapper;

    public CartController(CartService cartService, UserMapper userMapper) {
        this.cartService = cartService;
        this.userMapper = userMapper;
    }

    @GetMapping
    public ResponseEntity<?> list(Authentication authentication) {
        return asUser(authentication, userId -> ResponseEntity.ok(cartService.list(userId)));
    }

    @PostMapping
    public ResponseEntity<?> add(@RequestBody Map<String, Object> body, Authentication authentication) {
        return asUser(authentication, userId -> {
            Long productId = asLong(body.get("productId"));
            int quantity = body.get("quantity") == null ? 1 : asInt(body.get("quantity"));
            return ResponseEntity.ok(cartService.add(userId, productId, quantity));
        });
    }

    @PutMapping("/{cartItemId}")
    public ResponseEntity<?> updateQuantity(@PathVariable Long cartItemId,
                                            @RequestBody Map<String, Object> body,
                                            Authentication authentication) {
        return asUser(authentication, userId -> {
            var updated = cartService.updateQuantity(userId, cartItemId, asInt(body.get("quantity")));
            return updated == null
                    ? ResponseEntity.ok(Map.of("message", "Cart item removed"))
                    : ResponseEntity.ok(updated);
        });
    }

    @DeleteMapping("/{cartItemId}")
    public ResponseEntity<?> remove(@PathVariable Long cartItemId, Authentication authentication) {
        return asUser(authentication, userId -> {
            cartService.remove(userId, cartItemId);
            return ResponseEntity.ok(Map.of("message", "Cart item removed"));
        });
    }

    @DeleteMapping
    public ResponseEntity<?> clear(Authentication authentication) {
        return asUser(authentication, userId -> {
            cartService.clear(userId);
            return ResponseEntity.ok(Map.of("message", "Cart cleared"));
        });
    }

    private ResponseEntity<?> asUser(Authentication authentication, Function<Long, ResponseEntity<?>> action) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Not authenticated"));
        }
        User user = userMapper.selectByUsername(authentication.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unknown user"));
        }
        try {
            return action.apply(user.getId());
        } catch (InsufficientStockException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private Long asLong(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("productId is required");
        }
        return Long.valueOf(value.toString());
    }

    private int asInt(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("quantity is required");
        }
        return Integer.parseInt(value.toString());
    }
}

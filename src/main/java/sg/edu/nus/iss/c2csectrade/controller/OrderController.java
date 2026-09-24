package sg.edu.nus.iss.c2csectrade.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.iss.c2csectrade.entity.Order;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.exception.InsufficientStockException;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;
import sg.edu.nus.iss.c2csectrade.service.OrderService;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final UserMapper userMapper;

    public OrderController(OrderService orderService, UserMapper userMapper) {
        this.orderService = orderService;
        this.userMapper = userMapper;
    }

    /**
     * Merged checkout. Returns the list of orders produced, which is one per
     * seller represented in the selection.
     */
    @PostMapping("/checkout")
    public ResponseEntity<?> checkout(@RequestBody Map<String, Object> body, Authentication authentication) {
        return asUser(authentication, buyerId -> {
            @SuppressWarnings("unchecked")
            List<Object> raw = (List<Object>) body.get("cartItemIds");
            if (raw == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "cartItemIds is required"));
            }
            List<Long> ids = raw.stream().map(v -> Long.valueOf(v.toString())).toList();
            List<Order> orders = orderService.checkoutFromCart(buyerId, ids);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("orderCount", orders.size(), "orders", orders));
        });
    }

    /** Buy now, bypassing the cart. */
    @PostMapping("/buy-now")
    public ResponseEntity<?> buyNow(@RequestBody Map<String, Object> body, Authentication authentication) {
        return asUser(authentication, buyerId -> {
            Long productId = Long.valueOf(String.valueOf(body.get("productId")));
            int quantity = body.get("quantity") == null ? 1 : Integer.parseInt(body.get("quantity").toString());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(orderService.checkoutDirect(buyerId, productId, quantity));
        });
    }

    @GetMapping
    public ResponseEntity<?> myOrders(@RequestParam(defaultValue = "buyer") String role,
                                      Authentication authentication) {
        return asUser(authentication, userId -> ResponseEntity.ok(
                "seller".equalsIgnoreCase(role)
                        ? orderService.listForSeller(userId)
                        : orderService.listForBuyer(userId)));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<?> getOne(@PathVariable Long orderId, Authentication authentication) {
        return asUser(authentication, userId -> {
            Order order = orderService.getForUser(userId, orderId);
            return order == null
                    ? ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Order not found"))
                    : ResponseEntity.ok(order);
        });
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long orderId, Authentication authentication) {
        return asUser(authentication, buyerId -> ResponseEntity.ok(orderService.cancel(buyerId, orderId)));
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
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}

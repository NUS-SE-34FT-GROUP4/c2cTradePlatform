package sg.edu.nus.iss.c2csectrade.controller;

import sg.edu.nus.iss.c2csectrade.dto.SetPaymentPasswordRequest;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.exception.PaymentPasswordException;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;
import sg.edu.nus.iss.c2csectrade.service.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WalletService walletService;

    /**
     * Look up a user by username. Password hashes are write-only on the entity,
     * so they never appear in the response.
     */
    @GetMapping("/{username}")
    public ResponseEntity<User> getUserByUsername(@PathVariable String username) {
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(user);
    }


    /**
     * Change the caller's display name.
     */
    @PutMapping("/display-name")
    public ResponseEntity<?> updateDisplayName(@RequestBody Map<String, String> request, Authentication authentication) {
        try {
            String username = authentication.getName();
            String newDisplayName = request.get("displayName");

            if (newDisplayName == null || newDisplayName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Display name cannot be empty"));
            }

            newDisplayName = newDisplayName.trim();

            if (newDisplayName.length() < 2 || newDisplayName.length() > 20) {
                return ResponseEntity.badRequest().body(Map.of("message", "Display name must be 2 to 20 characters"));
            }

            if (containsSensitiveWords(newDisplayName)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Display name contains a reserved or offensive word"));
            }

            if (isDisplayNameExists(newDisplayName, username)) {
                return ResponseEntity.badRequest().body(Map.of("message", "This display name is already taken"));
            }

            User user = userMapper.selectByUsername(username);
            if (user == null) {
                return ResponseEntity.status(404).body(Map.of("message", "User not found"));
            }
            user.setUpdatedAt(java.time.Instant.now());
            user.setDisplayName(newDisplayName);
            userMapper.update(user);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Display name updated");
            response.put("displayName", newDisplayName);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Update failed: " + e.getMessage()));
        }
    }

    /**
     * Reserved and offensive words. The Chinese entries are data, not text to
     * translate: users can type Chinese display names, so they must stay.
     */
    private boolean containsSensitiveWords(String name) {
        String[] sensitiveWords = {
            "管理员", "admin", "系统", "system", "root", "超级", "客服",
            "官方", "垃圾", "傻逼", "操你", "妈的", "死", "杀", "色情",
            "政治"
        };

        String lowerName = name.toLowerCase();
        for (String word : sensitiveWords) {
            if (lowerName.contains(word.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether another user already has this display name.
     */
    private boolean isDisplayNameExists(String displayName, String currentUsername) {
        List<User> allUsers = userMapper.selectAll();
        return allUsers.stream()
                .filter(user -> !user.getUsername().equals(currentUsername))
                .anyMatch(user -> displayName.equals(user.getDisplayName()));
    }

    /**
     * The caller's own profile, including balance.
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userMapper.selectByUsername(username);

            if (user == null) {
                return ResponseEntity.status(404).body(Map.of("message", "User not found"));
            }

            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", user.getId());
            userInfo.put("username", user.getUsername());
            userInfo.put("displayName", user.getDisplayName());
            userInfo.put("email", user.getEmail());
            userInfo.put("avatarUrl", user.getAvatarUrl());
            userInfo.put("balance", user.getBalance() != null ? user.getBalance() : java.math.BigDecimal.ZERO);

            return ResponseEntity.ok(userInfo);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Failed to load user: " + e.getMessage()));
        }
    }

    /**
     * Whether the caller has set a payment password. The payment page uses
     * this to send first-time buyers to the setup page before they pay.
     */
    @GetMapping("/payment-password/check")
    public ResponseEntity<?> checkPaymentPassword(Authentication authentication) {
        return asUser(authentication, userId ->
                ResponseEntity.ok(Map.of("hasPaymentPassword", walletService.hasPaymentPassword(userId))));
    }

    /**
     * First-time setup. Refused once a payment password exists; use update.
     */
    @PostMapping("/payment-password/set")
    public ResponseEntity<?> setPaymentPassword(@RequestBody SetPaymentPasswordRequest request,
                                                Authentication authentication) {
        return asUser(authentication, userId -> {
            walletService.setPaymentPassword(userId, request.getPassword(), request.getConfirmPassword());
            return ResponseEntity.ok(Map.of("message", "Payment password set"));
        });
    }

    /**
     * Change the payment password; the old one is required.
     */
    @PutMapping("/payment-password/update")
    public ResponseEntity<?> updatePaymentPassword(@RequestBody Map<String, String> request,
                                                   Authentication authentication) {
        return asUser(authentication, userId -> {
            walletService.changePaymentPassword(userId,
                    request.get("oldPassword"), request.get("newPassword"), request.get("confirmPassword"));
            return ResponseEntity.ok(Map.of("message", "Payment password changed"));
        });
    }

    private ResponseEntity<?> asUser(Authentication authentication, Function<Long, ResponseEntity<?>> action) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Not authenticated"));
        }
        User user = userMapper.selectByUsername(authentication.getName());
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("message", "User not found"));
        }
        try {
            return action.apply(user.getId());
        } catch (PaymentPasswordException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}

package sg.edu.nus.iss.c2csectrade.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.exception.PaymentPasswordException;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;
import sg.edu.nus.iss.c2csectrade.service.WalletService;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Function;

/**
 * The caller's own balance and payment password. Paying is not exposed here:
 * it happens inside the order payment flow, which calls WalletService.pay.
 */
@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService walletService;
    private final UserMapper userMapper;

    public WalletController(WalletService walletService, UserMapper userMapper) {
        this.walletService = walletService;
        this.userMapper = userMapper;
    }

    @GetMapping
    public ResponseEntity<?> summary(Authentication authentication) {
        return asUser(authentication, userId -> ResponseEntity.ok(Map.of(
                "balance", walletService.getBalance(userId),
                "hasPaymentPassword", walletService.hasPaymentPassword(userId))));
    }

    @PutMapping("/payment-password")
    public ResponseEntity<?> setPaymentPassword(@RequestBody Map<String, String> body, Authentication authentication) {
        return asUser(authentication, userId -> {
            walletService.setPaymentPassword(userId, body.get("loginPassword"), body.get("paymentPassword"));
            return ResponseEntity.ok(Map.of("message", "Payment password saved"));
        });
    }

    @PostMapping("/top-up")
    public ResponseEntity<?> topUp(@RequestBody Map<String, Object> body, Authentication authentication) {
        return asUser(authentication, userId ->
                ResponseEntity.ok(Map.of("balance", walletService.topUp(userId, asAmount(body.get("amount"))))));
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
        } catch (PaymentPasswordException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private BigDecimal asAmount(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("amount is required");
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("amount must be a number");
        }
    }
}

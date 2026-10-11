package sg.edu.nus.iss.c2csectrade.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.iss.c2csectrade.exception.InsufficientStockException;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.service.BargainCampaignService;
import sg.edu.nus.iss.c2csectrade.service.BargainOfferService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.function.Function;

/**
 * Bargaining: price-cut campaigns and the offer threads that can turn a
 * negotiated price into a real order. Error shape follows OrderController:
 * a JSON {message} body with a status that tells the caller what to do.
 */
@RestController
@RequestMapping("/api/bargain")
public class BargainController {

    private final BargainCampaignService campaignService;
    private final BargainOfferService offerService;
    private final UserMapper userMapper;

    public BargainController(BargainCampaignService campaignService,
                             BargainOfferService offerService,
                             UserMapper userMapper) {
        this.campaignService = campaignService;
        this.offerService = offerService;
        this.userMapper = userMapper;
    }

    @PostMapping("/campaigns")
    public ResponseEntity<?> createCampaign(@RequestBody Map<String, Object> body, Authentication authentication) {
        return asUser(authentication, userId -> {
            if (body.get("productId") == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "productId is required"));
            }
            Long productId = Long.valueOf(String.valueOf(body.get("productId")));
            BigDecimal startPrice = body.get("startPrice") == null
                    ? null : new BigDecimal(String.valueOf(body.get("startPrice")));
            LocalDateTime deadline = body.get("deadline") == null ? null : parseWhen(body.get("deadline"));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(campaignService.create(userId, productId, startPrice, deadline));
        });
    }

    @PostMapping("/campaigns/{id}/help")
    public ResponseEntity<?> helpCampaign(@PathVariable Long id, Authentication authentication) {
        return asUser(authentication, userId -> ResponseEntity.ok(campaignService.help(id, userId)));
    }

    @GetMapping("/campaigns")
    public ResponseEntity<?> campaigns(@RequestParam(required = false) Long productId, Authentication authentication) {
        return asUser(authentication, userId -> {
            if (productId == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "productId is required"));
            }
            return ResponseEntity.ok(campaignService.listForProduct(productId));
        });
    }

    @PostMapping("/offers")
    public ResponseEntity<?> makeOffer(@RequestBody Map<String, Object> body, Authentication authentication) {
        return asUser(authentication, userId -> {
            if (body.get("productId") == null || body.get("price") == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "productId and price are required"));
            }
            Long productId = Long.valueOf(String.valueOf(body.get("productId")));
            BigDecimal price = new BigDecimal(String.valueOf(body.get("price")));
            return ResponseEntity.status(HttpStatus.CREATED).body(offerService.makeOffer(userId, productId, price));
        });
    }

    @PostMapping("/offers/{id}/counter")
    public ResponseEntity<?> counterOffer(@PathVariable Long id,
                                          @RequestBody Map<String, Object> body,
                                          Authentication authentication) {
        return asUser(authentication, userId -> {
            if (body.get("price") == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "price is required"));
            }
            BigDecimal price = new BigDecimal(String.valueOf(body.get("price")));
            return ResponseEntity.ok(offerService.counter(id, userId, price));
        });
    }

    /** Returns {orderId}: the negotiated order, which the page sends to payment. */
    @PostMapping("/offers/{id}/accept")
    public ResponseEntity<?> acceptOffer(@PathVariable Long id, Authentication authentication) {
        return asUser(authentication, userId -> ResponseEntity.ok(offerService.accept(id, userId)));
    }

    @PostMapping("/offers/{id}/reject")
    public ResponseEntity<?> rejectOffer(@PathVariable Long id, Authentication authentication) {
        return asUser(authentication, userId -> ResponseEntity.ok(offerService.reject(id, userId)));
    }

    @GetMapping("/offers")
    public ResponseEntity<?> offers(@RequestParam(required = false) Long productId, Authentication authentication) {
        return asUser(authentication, userId -> {
            if (productId == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "productId is required"));
            }
            return ResponseEntity.ok(offerService.listForProduct(productId, userId));
        });
    }

    /**
     * The contract shows deadlines with a zone suffix ("...Z") and the page
     * may equally send a local datetime; accept both.
     */
    private LocalDateTime parseWhen(Object raw) {
        String value = String.valueOf(raw);
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException e) {
            return LocalDateTime.parse(value);
        }
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

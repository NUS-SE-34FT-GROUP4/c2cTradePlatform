package sg.edu.nus.iss.c2csectrade.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ReviewResponse {
    private Long id;
    private Long orderId;
    private Long productId;
    private String productName;
    private Long buyerId;
    private String buyerName;
    private String buyerAvatar;
    private Long sellerId;
    private Integer productRating;
    private Integer sellerRating;
    private String comment;
    private List<String> reviewImages;
    private Boolean isAnonymous;
    private LocalDateTime createdAt;
}


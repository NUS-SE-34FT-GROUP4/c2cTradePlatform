package sg.edu.nus.iss.c2csectrade.dto;

import lombok.Data;

import java.util.List;

@Data
public class ReviewRequest {
    private Long orderId;
    private Long productId;
    private Integer productRating; // 1-5
    private Integer sellerRating; // 1-5
    private String comment;
    private List<String> reviewImages;
    private Boolean isAnonymous;
}


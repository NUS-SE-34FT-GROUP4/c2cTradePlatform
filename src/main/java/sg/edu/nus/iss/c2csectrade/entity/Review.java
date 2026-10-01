package sg.edu.nus.iss.c2csectrade.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class Review implements Serializable {
    private Long id;
    private Long orderId;
    private Long productId;
    private Long buyerId;
    private Long sellerId;
    private Integer productRating; // 商品评分 1-5
    private Integer sellerRating; // 卖家评分 1-5
    private String comment; // 评价内容
    private String reviewImages; // 评价图片，多个用逗号分隔
    private Boolean isAnonymous; // 是否匿名评价
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}


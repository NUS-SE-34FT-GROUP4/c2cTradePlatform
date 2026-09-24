package sg.edu.nus.iss.c2csectrade.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class Favorite implements Serializable {
    private Long id;
    private Long userId;
    private Long productId;
    private LocalDateTime createdAt;
    private Product product;
}

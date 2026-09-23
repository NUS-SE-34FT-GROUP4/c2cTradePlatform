package sg.edu.nus.iss.c2csectrade.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * A line in a user's cart. It deliberately carries no price: the price is read
 * from the listing and frozen into the order line at checkout, so a seller
 * changing the listing price is reflected until the moment of ordering.
 */
@Data
public class CartItem implements Serializable {
    private Long id;
    private Long userId;
    private Long productId;
    private Integer quantity;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Joined for display only; never persisted on this table. */
    private Product product;
}

package sg.edu.nus.iss.c2csectrade.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * One product line inside an order. {@code unitPriceSnapshot} and
 * {@code productName} are copied at creation time so that the order stays
 * readable and priced as it was, whatever happens to the listing afterwards.
 */
@Data
public class OrderItem implements Serializable {
    private Long id;
    private Long orderId;
    private Long productId;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPriceSnapshot;

    public BigDecimal getSubtotal() {
        if (unitPriceSnapshot == null || quantity == null) {
            return BigDecimal.ZERO;
        }
        return unitPriceSnapshot.multiply(BigDecimal.valueOf(quantity));
    }
}

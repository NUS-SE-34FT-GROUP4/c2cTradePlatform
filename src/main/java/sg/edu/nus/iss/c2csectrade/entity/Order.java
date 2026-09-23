package sg.edu.nus.iss.c2csectrade.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * An order always belongs to exactly one seller. A checkout that covers
 * products from N sellers produces N of these.
 */
@Data
public class Order implements Serializable {
    private Long id;
    private String orderNo;
    private Long buyerId;
    private Long sellerId;
    private String status;
    private BigDecimal totalAmount;
    private LocalDateTime expireAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<OrderItem> items = new ArrayList<>();

    /** Seller display name, joined for the order list; not persisted here. */
    private String sellerName;

    public OrderStatus statusAsEnum() {
        return status == null ? null : OrderStatus.valueOf(status);
    }

    public boolean isExpired(LocalDateTime now) {
        return OrderStatus.PENDING_PAYMENT.name().equals(status)
                && expireAt != null
                && expireAt.isBefore(now);
    }
}

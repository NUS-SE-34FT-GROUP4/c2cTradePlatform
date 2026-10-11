package sg.edu.nus.iss.c2csectrade.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A buyer-run price-cut campaign on one listing. Friends push the price down
 * in bounded steps until the floor or the deadline, whichever comes first.
 *
 * A campaign holds no stock and takes no money - it is a negotiation device,
 * so expiring or converting it releases nothing.
 */
@Data
public class BargainCampaign implements Serializable {
    private Long id;
    private Long productId;
    private Long buyerId;
    private BigDecimal startPrice;
    private BigDecimal currentPrice;
    private BigDecimal floorPrice;
    private String status; // ACTIVE, EXPIRED, CONVERTED
    private LocalDateTime deadline;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

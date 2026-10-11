package sg.edu.nus.iss.c2csectrade.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One entry in a negotiation thread. A thread is (product, buyer): the buyer
 * opens it, either party counters, and an accepted entry carries its price
 * into a real order. The latest entry's status is the state of the thread.
 */
@Data
public class BargainOffer implements Serializable {
    private Long id;
    private Long productId;
    private Long buyerId;
    private Long sellerId;
    private String from; // BUYER or SELLER - who made this entry
    private BigDecimal price;
    private String status; // PENDING, COUNTERED, ACCEPTED, REJECTED
    private Long orderId; // set when accepted - the negotiated order
    private LocalDateTime createdAt;
}

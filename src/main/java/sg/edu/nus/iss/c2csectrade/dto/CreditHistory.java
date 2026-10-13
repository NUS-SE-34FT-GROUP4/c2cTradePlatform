package sg.edu.nus.iss.c2csectrade.dto;

import lombok.Data;

@Data
public class CreditHistory {
    private Long sellerId;
    private long completedOrders;
    private long reviewCount;
    private long ratingSum;
}

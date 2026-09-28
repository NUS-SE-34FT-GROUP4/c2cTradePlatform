package sg.edu.nus.iss.c2csectrade.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One money movement against an order. The amount is always positive; the
 * direction is given by {@code transactionType}, so a refunded order still
 * shows what was originally paid rather than a mutated record.
 */
@Data
public class Transaction implements Serializable {
    private Long id;
    private Long orderId;
    private BigDecimal amount;
    private String paymentMethod;
    private String transactionType;
    private String status;
    private LocalDateTime createdAt;

    public TransactionType typeAsEnum() {
        return transactionType == null ? null : TransactionType.valueOf(transactionType);
    }

    public boolean isSuccessful() {
        return "SUCCESS".equals(status);
    }
}

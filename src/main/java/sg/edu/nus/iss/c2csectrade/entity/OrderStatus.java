package sg.edu.nus.iss.c2csectrade.entity;

/**
 * Order lifecycle. Sprint 2 only creates orders in PENDING_PAYMENT and moves
 * them to CANCELLED or EXPIRED; the remaining transitions land in Sprint 3.
 */
public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    SHIPPED,
    COMPLETED,
    CANCELLED,
    EXPIRED;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == EXPIRED;
    }

    /** Stock is still only reserved, not yet deducted, while the order is unpaid. */
    public boolean holdsReservation() {
        return this == PENDING_PAYMENT;
    }
}

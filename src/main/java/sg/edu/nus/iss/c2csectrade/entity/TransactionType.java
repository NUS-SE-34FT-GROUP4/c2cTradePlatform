package sg.edu.nus.iss.c2csectrade.entity;

/**
 * The three movements an order's money can make: in from the buyer, on to the
 * seller once the buyer confirms receipt, or back to the buyer instead.
 *
 * Each is its own row, so the ledger stays append-only and what is still held
 * against the order is the difference between them.
 */
public enum TransactionType {
    PAYMENT,
    PAYOUT,
    REFUND
}

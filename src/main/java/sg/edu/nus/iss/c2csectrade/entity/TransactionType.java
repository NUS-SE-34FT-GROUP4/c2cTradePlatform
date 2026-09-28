package sg.edu.nus.iss.c2csectrade.entity;

/** A payment and its refund are separate rows, so the ledger stays append-only. */
public enum TransactionType {
    PAYMENT,
    REFUND
}

package sg.edu.nus.iss.c2csectrade.dto;

/** Seller reputation, recalculated from committed trading history on every read. */
public record CreditSummary(Integer score, long completedOrders, long reviewCount) {}

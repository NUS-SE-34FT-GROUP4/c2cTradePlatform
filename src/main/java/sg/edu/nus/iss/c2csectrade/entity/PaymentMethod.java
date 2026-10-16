package sg.edu.nus.iss.c2csectrade.entity;

/**
 * The simulated payment methods. The proposal named four; PayNow, the local
 * bank transfer, was added in Sprint 4 — and adding it took a strategy class
 * and this value, with no change to the order flow or the payment service.
 *
 * Each value has a strategy implementation behind it; the enum itself is only
 * the identifier the request carries and the ledger stores.
 *
 * The stored token is lower case ("balance", "alipay", ...) because that is
 * what the payment page sends.
 */
public enum PaymentMethod {
    BALANCE,
    ALIPAY,
    WECHAT,
    BANK,
    PAYNOW;

    /** Only the account balance is settled inside this system. */
    public boolean isInternal() {
        return this == BALANCE;
    }

    public String code() {
        return name().toLowerCase();
    }

    public static PaymentMethod of(String code) {
        if (code == null) {
            throw new IllegalArgumentException("Payment method is required");
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported payment method: " + code);
        }
    }
}

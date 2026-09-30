package sg.edu.nus.iss.c2csectrade.entity;

/**
 * The four simulated payment methods from the proposal. Each becomes a strategy
 * implementation in Sprint 3; the enum is only the identifier the request
 * carries and the ledger stores.
 *
 * The stored token is lower case ("balance", "alipay", ...) because that is
 * what the payment page sends.
 */
public enum PaymentMethod {
    BALANCE,
    ALIPAY,
    WECHAT,
    BANK;

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

package sg.edu.nus.iss.c2csectrade.exception;

/** Raised when the account balance cannot cover a payment. */
public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException(String message) {
        super(message);
    }
}

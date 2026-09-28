package sg.edu.nus.iss.c2csectrade.exception;

/** Raised when a payment password is missing, malformed, wrong or locked out. */
public class PaymentPasswordException extends RuntimeException {
    public PaymentPasswordException(String message) {
        super(message);
    }
}

package sg.edu.nus.iss.c2csectrade.exception;

/** Raised when the available stock cannot cover what the buyer asked for. */
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String message) {
        super(message);
    }
}

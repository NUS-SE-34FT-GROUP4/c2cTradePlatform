package sg.edu.nus.iss.c2csectrade.exception;

/** Raised when a seller tries to act on a listing that is not theirs. */
public class ProductAccessDeniedException extends RuntimeException {
    public ProductAccessDeniedException(String message) {
        super(message);
    }
}

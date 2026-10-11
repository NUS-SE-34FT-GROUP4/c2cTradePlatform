package sg.edu.nus.iss.c2csectrade.exception;

import org.springframework.http.HttpStatus;

/** A rejected report or moderation request; carries the status and title of the error body. */
public class ModerationException extends RuntimeException {
    private final HttpStatus status;
    private final String title;

    private ModerationException(HttpStatus status, String title, String message) {
        super(message);
        this.status = status;
        this.title = title;
    }

    public static ModerationException badRequest(String message) {
        return new ModerationException(HttpStatus.BAD_REQUEST, "Invalid request", message);
    }

    public static ModerationException notFound(String message) {
        return new ModerationException(HttpStatus.NOT_FOUND, "Not found", message);
    }

    public static ModerationException conflict(String message) {
        return new ModerationException(HttpStatus.CONFLICT, "Conflict", message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }
}

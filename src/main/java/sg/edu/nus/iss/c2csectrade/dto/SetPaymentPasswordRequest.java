package sg.edu.nus.iss.c2csectrade.dto;

import lombok.Data;

/** First-time payment password setup; the confirmation guards against a typo locking the user out. */
@Data
public class SetPaymentPasswordRequest {
    private String password;
    private String confirmPassword;
}

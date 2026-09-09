package sg.edu.nus.iss.c2csectrade.payload;

import lombok.Data;

@Data
public class RegisterRequest {
    private String username;
    private String displayName;
    private String email;
    private String password;
    private String captchaId;
    private String captchaCode;
}

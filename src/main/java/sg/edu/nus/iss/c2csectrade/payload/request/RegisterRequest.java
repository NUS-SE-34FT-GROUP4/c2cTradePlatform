package sg.edu.nus.iss.c2csectrade.payload.request;

import lombok.Data;

@Data
public class RegisterRequest {
    private String username;
    private String password;
    private String email;
    private String captchaId;
    private String captchaCode;
}


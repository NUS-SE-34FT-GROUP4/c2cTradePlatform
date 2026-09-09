package sg.edu.nus.iss.c2csectrade.service;

import sg.edu.nus.iss.c2csectrade.payload.RegisterRequest;
import sg.edu.nus.iss.c2csectrade.payload.PasswordResetRequest;
import sg.edu.nus.iss.c2csectrade.entity.User;

public interface AuthService {
    User registerUser(RegisterRequest registerRequest);
    User getUserByUsername(String username);
    boolean resetPassword(PasswordResetRequest request);
}


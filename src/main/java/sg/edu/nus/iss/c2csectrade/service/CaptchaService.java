package sg.edu.nus.iss.c2csectrade.service;

public interface CaptchaService {
    void save(String id, String code, long ttlSeconds);
    boolean verifyAndConsume(String id, String code);
}


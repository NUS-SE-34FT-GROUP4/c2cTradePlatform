package sg.edu.nus.iss.c2csectrade.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.exception.InsufficientBalanceException;
import sg.edu.nus.iss.c2csectrade.exception.PaymentPasswordException;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Internal account balance and the payment password that guards it (Proposal 5.1).
 *
 * The payment password is a separate six-digit secret with its own BCrypt hash,
 * so a stolen login session or login password alone cannot spend the balance.
 * Six digits is only a million combinations, so repeated wrong attempts lock
 * the account's payments for a while.
 *
 * Payment methods (M3) call {@link #pay} and never touch the balance column.
 */
@Service
public class WalletService {

    static final int MAX_ATTEMPTS = 5;
    static final Duration LOCKOUT = Duration.ofMinutes(15);
    static final BigDecimal MAX_TOP_UP = new BigDecimal("10000.00");
    private static final Pattern SIX_DIGITS = Pattern.compile("\\d{6}");

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    // In memory because staging runs a single backend instance. A restart
    // clears it; move it to Redis if the backend is ever scaled out.
    private final Map<Long, FailedAttempts> failures = new ConcurrentHashMap<>();

    @Autowired
    public WalletService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this(userMapper, passwordEncoder, Clock.systemUTC());
    }

    WalletService(UserMapper userMapper, PasswordEncoder passwordEncoder, Clock clock) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    public BigDecimal getBalance(Long userId) {
        BigDecimal balance = load(userId).getBalance();
        return balance != null ? balance : BigDecimal.ZERO;
    }

    public boolean hasPaymentPassword(Long userId) {
        return load(userId).getPaymentPasswordHash() != null;
    }

    /**
     * Sets or resets the payment password. Re-entering the login password is
     * what authorises it, so a forgotten payment password can be reset without
     * knowing the old one, but an unattended logged-in browser cannot.
     */
    public void setPaymentPassword(Long userId, String loginPassword, String paymentPassword) {
        if (paymentPassword == null || !SIX_DIGITS.matcher(paymentPassword).matches()) {
            throw new PaymentPasswordException("Payment password must be exactly 6 digits");
        }
        User user = load(userId);
        if (loginPassword == null || !passwordEncoder.matches(loginPassword, user.getPasswordHash())) {
            throw new PaymentPasswordException("Login password is incorrect");
        }
        userMapper.updatePaymentPasswordHash(userId, passwordEncoder.encode(paymentPassword));
        failures.remove(userId);
    }

    /** Throws unless the payment password is right and the account is not locked out. */
    public void verifyPaymentPassword(Long userId, String paymentPassword) {
        User user = load(userId);
        if (user.getPaymentPasswordHash() == null) {
            throw new PaymentPasswordException("Set a payment password before paying");
        }
        Instant now = clock.instant();
        FailedAttempts attempts = failures.get(userId);
        if (attempts != null && attempts.lockedUntil != null) {
            if (now.isBefore(attempts.lockedUntil)) {
                long minutes = Math.max(1, Duration.between(now, attempts.lockedUntil).toMinutes());
                throw new PaymentPasswordException(
                        "Too many wrong payment passwords. Try again in " + minutes + " minute(s)");
            }
            failures.remove(userId);
        }
        if (paymentPassword == null || !passwordEncoder.matches(paymentPassword, user.getPaymentPasswordHash())) {
            int remaining = recordFailure(userId, now);
            throw new PaymentPasswordException(remaining > 0
                    ? "Payment password is incorrect, " + remaining + " attempt(s) left"
                    : "Payment password is incorrect. Payments are locked for " + LOCKOUT.toMinutes() + " minutes");
        }
        failures.remove(userId);
    }

    /**
     * Verifies the payment password, then deducts the amount.
     *
     * @return the balance after payment
     */
    @Transactional
    public BigDecimal pay(Long userId, String paymentPassword, BigDecimal amount) {
        requireValidAmount(amount);
        verifyPaymentPassword(userId, paymentPassword);
        if (userMapper.debitBalance(userId, amount) == 0) {
            throw new InsufficientBalanceException("Insufficient balance");
        }
        return getBalance(userId);
    }

    /**
     * Adds money to an account: refunds, and seller payout once a buyer confirms receipt.
     *
     * @return the balance after the credit
     */
    @Transactional
    public BigDecimal credit(Long userId, BigDecimal amount) {
        requireValidAmount(amount);
        if (userMapper.creditBalance(userId, amount) == 0) {
            throw new IllegalArgumentException("User not found");
        }
        return getBalance(userId);
    }

    /** Simulated top-up; no real payment gateway is involved (Proposal, out of scope). */
    @Transactional
    public BigDecimal topUp(Long userId, BigDecimal amount) {
        requireValidAmount(amount);
        if (amount.compareTo(MAX_TOP_UP) > 0) {
            throw new IllegalArgumentException("A single top-up cannot exceed " + MAX_TOP_UP);
        }
        return credit(userId, amount);
    }

    private int recordFailure(Long userId, Instant now) {
        FailedAttempts attempts = failures.compute(userId, (id, previous) -> {
            FailedAttempts next = previous != null ? previous : new FailedAttempts();
            next.count++;
            if (next.count >= MAX_ATTEMPTS) {
                next.lockedUntil = now.plus(LOCKOUT);
            }
            return next;
        });
        return Math.max(0, MAX_ATTEMPTS - attempts.count);
    }

    private void requireValidAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Amount cannot have more than two decimal places");
        }
    }

    private User load(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        return user;
    }

    private static final class FailedAttempts {
        int count;
        Instant lockedUntil;
    }
}

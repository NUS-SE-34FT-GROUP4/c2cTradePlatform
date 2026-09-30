package sg.edu.nus.iss.c2csectrade.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.exception.InsufficientBalanceException;
import sg.edu.nus.iss.c2csectrade.exception.PaymentPasswordException;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    private static final Long USER_ID = 1L;
    private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");

    @Mock private UserMapper userMapper;
    @Mock private Clock clock;

    // Real hashing, low cost factor so the suite stays fast
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private WalletService walletService;
    private User user;

    @BeforeEach
    void setUp() {
        walletService = new WalletService(userMapper, encoder, clock);
        user = new User();
        user.setId(USER_ID);
        user.setUsername("alice");
        user.setPasswordHash(encoder.encode("login-pass"));
        user.setBalance(new BigDecimal("100.00"));
        lenient().when(userMapper.selectById(USER_ID)).thenReturn(user);
        lenient().when(clock.instant()).thenReturn(T0);
    }

    private void withPaymentPassword(String pin) {
        user.setPaymentPasswordHash(encoder.encode(pin));
    }

    @Test
    @DisplayName("The payment password is stored as its own BCrypt hash, never in plain text")
    void setPaymentPasswordStoresHash() {
        walletService.setPaymentPassword(USER_ID, "login-pass", "123456");

        verify(userMapper).updatePaymentPasswordHash(eq(USER_ID), argThat(hash ->
                !hash.equals("123456") && encoder.matches("123456", hash)));
    }

    @Test
    @DisplayName("Setting the payment password requires the correct login password")
    void setPaymentPasswordChecksLoginPassword() {
        PaymentPasswordException error = assertThrows(PaymentPasswordException.class,
                () -> walletService.setPaymentPassword(USER_ID, "wrong", "123456"));

        assertTrue(error.getMessage().contains("Login password"));
        verify(userMapper, never()).updatePaymentPasswordHash(any(), any());
    }

    @Test
    @DisplayName("The payment password must be exactly six digits")
    void paymentPasswordMustBeSixDigits() {
        for (String bad : new String[]{null, "12345", "1234567", "12345a", "      "}) {
            assertThrows(PaymentPasswordException.class,
                    () -> walletService.setPaymentPassword(USER_ID, "login-pass", bad), "accepted: " + bad);
        }
        verify(userMapper, never()).updatePaymentPasswordHash(any(), any());
    }

    @Test
    @DisplayName("hasPaymentPassword reflects whether a hash is stored")
    void hasPaymentPassword() {
        assertFalse(walletService.hasPaymentPassword(USER_ID));
        withPaymentPassword("123456");
        assertTrue(walletService.hasPaymentPassword(USER_ID));
    }

    @Test
    @DisplayName("Paying with the right password deducts the amount")
    void payDeductsBalance() {
        withPaymentPassword("123456");
        when(userMapper.debitBalance(USER_ID, new BigDecimal("30.00"))).thenReturn(1);

        walletService.pay(USER_ID, "123456", new BigDecimal("30.00"));

        verify(userMapper).debitBalance(USER_ID, new BigDecimal("30.00"));
    }

    @Test
    @DisplayName("Paying is refused when no payment password has been set")
    void payRequiresPaymentPasswordSet() {
        PaymentPasswordException error = assertThrows(PaymentPasswordException.class,
                () -> walletService.pay(USER_ID, "123456", new BigDecimal("10")));

        assertTrue(error.getMessage().contains("Set a payment password"));
        verify(userMapper, never()).debitBalance(any(), any());
    }

    @Test
    @DisplayName("A wrong payment password deducts nothing and reports attempts left")
    void wrongPasswordDeductsNothing() {
        withPaymentPassword("123456");

        PaymentPasswordException error = assertThrows(PaymentPasswordException.class,
                () -> walletService.pay(USER_ID, "000000", new BigDecimal("10")));

        assertTrue(error.getMessage().contains("4 attempt(s) left"));
        verify(userMapper, never()).debitBalance(any(), any());
    }

    @Test
    @DisplayName("When the balance is too low the payment fails with InsufficientBalanceException")
    void insufficientBalance() {
        withPaymentPassword("123456");
        when(userMapper.debitBalance(eq(USER_ID), any())).thenReturn(0);

        assertThrows(InsufficientBalanceException.class,
                () -> walletService.pay(USER_ID, "123456", new BigDecimal("500.00")));
    }

    @Test
    @DisplayName("Five wrong attempts lock payments, even for the right password, until the lockout ends")
    void lockoutAfterFiveFailures() {
        withPaymentPassword("123456");
        for (int i = 0; i < WalletService.MAX_ATTEMPTS; i++) {
            assertThrows(PaymentPasswordException.class, () -> walletService.verifyPaymentPassword(USER_ID, "000000"));
        }

        PaymentPasswordException locked = assertThrows(PaymentPasswordException.class,
                () -> walletService.verifyPaymentPassword(USER_ID, "123456"));
        assertTrue(locked.getMessage().contains("Try again in"));

        when(clock.instant()).thenReturn(T0.plus(WalletService.LOCKOUT).plusSeconds(1));
        assertDoesNotThrow(() -> walletService.verifyPaymentPassword(USER_ID, "123456"));
    }

    @Test
    @DisplayName("A correct password resets the failure count")
    void successResetsFailureCount() {
        withPaymentPassword("123456");
        for (int i = 0; i < WalletService.MAX_ATTEMPTS - 1; i++) {
            assertThrows(PaymentPasswordException.class, () -> walletService.verifyPaymentPassword(USER_ID, "000000"));
        }
        walletService.verifyPaymentPassword(USER_ID, "123456");

        PaymentPasswordException error = assertThrows(PaymentPasswordException.class,
                () -> walletService.verifyPaymentPassword(USER_ID, "000000"));
        assertTrue(error.getMessage().contains("4 attempt(s) left"));
    }

    @Test
    @DisplayName("Resetting the payment password clears a lockout")
    void resetClearsLockout() {
        withPaymentPassword("123456");
        for (int i = 0; i < WalletService.MAX_ATTEMPTS; i++) {
            assertThrows(PaymentPasswordException.class, () -> walletService.verifyPaymentPassword(USER_ID, "000000"));
        }

        walletService.setPaymentPassword(USER_ID, "login-pass", "654321");
        withPaymentPassword("654321");

        assertDoesNotThrow(() -> walletService.verifyPaymentPassword(USER_ID, "654321"));
    }

    @Test
    @DisplayName("Amounts must be positive with at most two decimal places")
    void amountValidation() {
        withPaymentPassword("123456");
        for (String bad : new String[]{"0", "-5", "1.005"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> walletService.pay(USER_ID, "123456", new BigDecimal(bad)), "accepted: " + bad);
        }
        assertThrows(IllegalArgumentException.class, () -> walletService.pay(USER_ID, "123456", null));
        verify(userMapper, never()).debitBalance(any(), any());
    }

    @Test
    @DisplayName("Top-up credits the account and is capped per transaction")
    void topUp() {
        when(userMapper.creditBalance(USER_ID, new BigDecimal("50"))).thenReturn(1);

        walletService.topUp(USER_ID, new BigDecimal("50"));
        verify(userMapper).creditBalance(USER_ID, new BigDecimal("50"));

        assertThrows(IllegalArgumentException.class,
                () -> walletService.topUp(USER_ID, new BigDecimal("10000.01")));
    }

    @Test
    @DisplayName("Crediting an unknown user fails")
    void creditUnknownUser() {
        when(userMapper.creditBalance(eq(99L), any())).thenReturn(0);

        assertThrows(IllegalArgumentException.class, () -> walletService.credit(99L, BigDecimal.TEN));
    }

    @Test
    @DisplayName("A missing balance reads as zero; an unknown user is rejected")
    void getBalance() {
        assertEquals(new BigDecimal("100.00"), walletService.getBalance(USER_ID));
        user.setBalance(null);
        assertEquals(BigDecimal.ZERO, walletService.getBalance(USER_ID));
        assertThrows(IllegalArgumentException.class, () -> walletService.getBalance(42L));
    }
}

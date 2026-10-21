package sg.edu.nus.iss.c2csectrade.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import sg.edu.nus.iss.c2csectrade.dto.SetPaymentPasswordRequest;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.exception.PaymentPasswordException;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;
import sg.edu.nus.iss.c2csectrade.service.CreditService;
import sg.edu.nus.iss.c2csectrade.service.WalletService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock private UserMapper userMapper;
    @Mock private CreditService creditService;
    @Mock private WalletService walletService;
    @InjectMocks private UserController controller;

    private static final String USERNAME = "yuxin";

    private Authentication signedIn() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(USERNAME);
        return authentication;
    }

    private User user(BigDecimal balance) {
        User user = new User();
        user.setId(1L);
        user.setUsername(USERNAME);
        user.setDisplayName("Yuxin");
        user.setEmail("yuxin@test.com");
        user.setAvatarUrl("https://example.test/a.png");
        user.setBalance(balance);
        user.setCreatedAt(Instant.parse("2026-09-01T00:00:00Z"));
        return user;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(ResponseEntity<?> response) {
        return (Map<String, Object>) response.getBody();
    }

    @Test
    @DisplayName("A username that is not registered gives 404, not an empty user")
    void lookupOfAnUnknownUsernameIsNotFound() {
        when(userMapper.selectByUsername("nobody")).thenReturn(null);

        assertEquals(404, controller.getUserByUsername("nobody").getStatusCode().value());
    }

    @Test
    @DisplayName("A public profile carries no email and no balance")
    void publicProfileHidesPrivateFields() {
        when(userMapper.selectByUsername(USERNAME)).thenReturn(user(new BigDecimal("500.00")));

        Map<String, Object> profile = body(controller.getPublicProfile(USERNAME));

        assertEquals("Yuxin", profile.get("displayName"));
        assertFalse(profile.containsKey("email"));
        assertFalse(profile.containsKey("balance"));
        assertFalse(profile.containsKey("banReason"));
    }

    @Test
    @DisplayName("A public profile of an unknown user gives 404")
    void publicProfileOfAnUnknownUserIsNotFound() {
        when(userMapper.selectByUsername("nobody")).thenReturn(null);

        assertEquals(404, controller.getPublicProfile("nobody").getStatusCode().value());
    }

    @Test
    @DisplayName("A display name is saved once it passes every check")
    void displayNameIsSaved() {
        when(userMapper.selectAll()).thenReturn(List.of(user(null)));
        when(userMapper.selectByUsername(USERNAME)).thenReturn(user(null));

        ResponseEntity<?> response =
                controller.updateDisplayName(Map.of("displayName", "  Yuxin Z  "), signedIn());

        assertEquals(200, response.getStatusCode().value());
        assertEquals("Yuxin Z", body(response).get("displayName"));
        // The surrounding spaces are trimmed before the name is stored.
        verify(userMapper).update(argThat(saved -> "Yuxin Z".equals(saved.getDisplayName())
                && saved.getUpdatedAt() != null));
    }

    @Test
    @DisplayName("An empty display name is refused")
    void displayNameCannotBeEmpty() {
        ResponseEntity<?> response = controller.updateDisplayName(Map.of("displayName", "   "), signedIn());

        assertEquals(400, response.getStatusCode().value());
        verify(userMapper, never()).update(any());
    }

    @Test
    @DisplayName("A display name shorter than two or longer than twenty characters is refused")
    void displayNameLengthIsBounded() {
        assertEquals(400, controller.updateDisplayName(Map.of("displayName", "A"), signedIn())
                .getStatusCode().value());
        assertEquals(400, controller.updateDisplayName(Map.of("displayName", "A".repeat(21)), signedIn())
                .getStatusCode().value());
        verify(userMapper, never()).update(any());
    }

    @Test
    @DisplayName("A reserved word in a display name is refused")
    void displayNameCannotImpersonateStaff() {
        ResponseEntity<?> response = controller.updateDisplayName(Map.of("displayName", "Admin Helper"), signedIn());

        assertEquals(400, response.getStatusCode().value());
        verify(userMapper, never()).update(any());
    }

    @Test
    @DisplayName("A display name another account already uses is refused")
    void displayNameMustBeFree() {
        User other = new User();
        other.setUsername("someone-else");
        other.setDisplayName("Taken");
        when(userMapper.selectAll()).thenReturn(List.of(other));

        ResponseEntity<?> response = controller.updateDisplayName(Map.of("displayName", "Taken"), signedIn());

        assertEquals(400, response.getStatusCode().value());
        verify(userMapper, never()).update(any());
    }

    @Test
    @DisplayName("The caller's own profile reports a missing balance as zero")
    void ownProfileReportsZeroWhenNoBalanceIsSet() {
        when(userMapper.selectByUsername(USERNAME)).thenReturn(user(null));

        Map<String, Object> me = body(controller.getCurrentUser(signedIn()));

        assertEquals(BigDecimal.ZERO, me.get("balance"));
        assertEquals("yuxin@test.com", me.get("email"));
        assertTrue(me.containsKey("credit"));
        verify(creditService).forSeller(1L);
    }

    @Test
    @DisplayName("An account that vanished mid-session gives 404 rather than a server error")
    void ownProfileOfAVanishedAccountIsNotFound() {
        when(userMapper.selectByUsername(USERNAME)).thenReturn(null);

        assertEquals(404, controller.getCurrentUser(signedIn()).getStatusCode().value());
    }

    @Test
    @DisplayName("A failure below the controller surfaces as 500, not as a broken response")
    void ownProfileSurfacesFailuresAsServerError() {
        when(userMapper.selectByUsername(USERNAME)).thenThrow(new RuntimeException("database down"));

        assertEquals(500, controller.getCurrentUser(signedIn()).getStatusCode().value());
    }

    @Test
    @DisplayName("The payment page can ask whether a payment password is set")
    void paymentPasswordCheckAnswersForTheCaller() {
        when(userMapper.selectByUsername(USERNAME)).thenReturn(user(null));
        when(walletService.hasPaymentPassword(1L)).thenReturn(true);

        Map<String, Object> answer = body(controller.checkPaymentPassword(signedIn()));

        assertEquals(true, answer.get("hasPaymentPassword"));
    }

    @Test
    @DisplayName("Setting a payment password passes both entries to the wallet")
    void setPaymentPasswordDelegatesToTheWallet() {
        when(userMapper.selectByUsername(USERNAME)).thenReturn(user(null));
        SetPaymentPasswordRequest request = new SetPaymentPasswordRequest();
        request.setPassword("123456");
        request.setConfirmPassword("123456");

        assertEquals(200, controller.setPaymentPassword(request, signedIn()).getStatusCode().value());
        verify(walletService).setPaymentPassword(1L, "123456", "123456");
    }

    @Test
    @DisplayName("A refused payment password comes back as 400 with the reason")
    void aRefusedPaymentPasswordIsABadRequest() {
        when(userMapper.selectByUsername(USERNAME)).thenReturn(user(null));
        doThrow(new PaymentPasswordException("The two entries do not match"))
                .when(walletService).setPaymentPassword(anyLong(), anyString(), anyString());
        SetPaymentPasswordRequest request = new SetPaymentPasswordRequest();
        request.setPassword("123456");
        request.setConfirmPassword("654321");

        ResponseEntity<?> response = controller.setPaymentPassword(request, signedIn());

        assertEquals(400, response.getStatusCode().value());
        assertEquals("The two entries do not match", body(response).get("message"));
    }

    @Test
    @DisplayName("Changing a payment password requires the old one")
    void changePaymentPasswordPassesTheOldEntryOn() {
        when(userMapper.selectByUsername(USERNAME)).thenReturn(user(null));

        ResponseEntity<?> response = controller.updatePaymentPassword(
                Map.of("oldPassword", "111111", "newPassword", "222222", "confirmPassword", "222222"), signedIn());

        assertEquals(200, response.getStatusCode().value());
        verify(walletService).changePaymentPassword(1L, "111111", "222222", "222222");
    }

    @Test
    @DisplayName("Without a session the wallet is never touched")
    void anUnauthenticatedCallerIsRefused() {
        ResponseEntity<?> response = controller.checkPaymentPassword(null);

        assertEquals(401, response.getStatusCode().value());
        verifyNoInteractions(walletService);
    }

    @Test
    @DisplayName("A session naming an account that no longer exists is refused")
    void aSessionForAVanishedAccountIsRefused() {
        when(userMapper.selectByUsername(USERNAME)).thenReturn(null);

        assertEquals(404, controller.checkPaymentPassword(signedIn()).getStatusCode().value());
        verifyNoInteractions(walletService);
    }
}

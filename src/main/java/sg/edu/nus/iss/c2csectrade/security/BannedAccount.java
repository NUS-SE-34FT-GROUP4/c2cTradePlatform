package sg.edu.nus.iss.c2csectrade.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * A banned account: Spring sees it as locked, and the reason travels with it so
 * every refusal can show it (docs/api/moderation.md §5).
 */
public class BannedAccount {
    /** Request attribute the JWT filter sets when the token belongs to a banned account. */
    public static final String REQUEST_ATTRIBUTE = BannedAccount.class.getName();
    private static final ObjectMapper JSON = new ObjectMapper();

    private BannedAccount() {
    }

    /** Spring user that also carries the ban reason; locked while banned. */
    public static class AccountUser extends User {
        private final String banReason;

        public AccountUser(String username, String password, boolean banned, String banReason,
                           Collection<? extends GrantedAuthority> authorities) {
            super(username, password, true, true, true, !banned, authorities);
            this.banReason = banReason;
        }

        public String getBanReason() {
            return banReason;
        }
    }

    public static boolean flagged(HttpServletRequest request) {
        return request.getAttribute(REQUEST_ATTRIBUTE) != null;
    }

    /** Writes 403 {"error": "ACCOUNT_BANNED", "message": "Your account is suspended: ..."}. */
    public static void write(HttpServletResponse response, String reason) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        JSON.writeValue(response.getWriter(), body(reason));
    }

    public static Map<String, String> body(String reason) {
        String message = reason == null || reason.isBlank()
                ? "Your account is suspended"
                : "Your account is suspended: " + reason;
        return Map.of("error", "ACCOUNT_BANNED", "message", message);
    }
}

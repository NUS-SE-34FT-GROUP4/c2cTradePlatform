package sg.edu.nus.iss.c2csectrade.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JWT issuance and validation (Sprint 1 - user authentication).
 */
class JwtTokenProviderTest {

    private static final String TEST_SECRET = "c3ByaW50MS11bml0LXRlc3Qtc2lnbmluZy1rZXktZG8tbm90LXVzZS1pbi1wcm9kdWN0aW9uLTAxMjM0NTY3ODk="; // gitleaks:allow — test-only signing key, never used outside unit tests

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationMs", 3600000);
    }

    private Authentication authenticationFor(String username) {
        UserDetails principal = new User(username, "irrelevant",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    @DisplayName("A generated token carries the authenticated username as its subject")
    void generatedTokenCarriesUsername() {
        String token = jwtTokenProvider.generateToken(authenticationFor("alice"));

        assertNotNull(token);
        assertEquals("alice", jwtTokenProvider.getUsernameFromToken(token));
    }

    @Test
    @DisplayName("A token issued by the provider validates successfully")
    void issuedTokenIsValid() {
        String token = jwtTokenProvider.generateToken(authenticationFor("bob"));

        assertTrue(jwtTokenProvider.validateToken(token));
    }

    @Test
    @DisplayName("A malformed token is rejected")
    void malformedTokenIsRejected() {
        assertFalse(jwtTokenProvider.validateToken("this-is-not-a-jwt"));
    }

    @Test
    @DisplayName("A token signed with a different key is rejected")
    void tokenSignedWithAnotherKeyIsRejected() {
        String foreign = Jwts.builder()
                .setSubject("mallory")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Keys.hmacShaKeyFor(
                        "a-completely-different-signing-key-of-sufficient-length-000000000".getBytes()),
                        SignatureAlgorithm.HS512)
                .compact();

        assertFalse(jwtTokenProvider.validateToken(foreign));
    }

    @Test
    @DisplayName("An expired token is rejected")
    void expiredTokenIsRejected() {
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationMs", -1000);
        String expired = jwtTokenProvider.generateToken(authenticationFor("carol"));

        assertFalse(jwtTokenProvider.validateToken(expired));
    }
}

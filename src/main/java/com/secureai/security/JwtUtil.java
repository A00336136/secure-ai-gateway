package com.secureai.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT Utility — token generation, parsing, and validation.
 *
 * Token structure:
 *  - subject  : username
 *  - claim    : "role" (e.g. "USER", "ADMIN")
 *  - issued   : current timestamp
 *  - expiry   : now + jwt.expiration ms (field: expirationMs)
 *  - signature: HMAC-SHA256 with key from jwt.secret
 *
 * Callers and the methods they require:
 *  AuthService              → generateToken(username, role), getExpirationSeconds()
 *  JwtAuthenticationFilter  → validateToken(token), getUsernameFromToken(token),
 *                             getRoleFromToken(token)
 */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    private static final String ROLE_CLAIM = "role";

    @Value("${jwt.secret}")
    private String secret;

    /** Expiration in milliseconds (set in application.properties, e.g. 3600000 = 1 hour).
     *  Named expirationMs so ReflectionTestUtils.setField() in JwtUtilTest can locate it. */
    @Value("${jwt.expiration}")
    private long expirationMs;

    // ─────────────────────────────────────────────────────────────────────────
    // Key
    // ─────────────────────────────────────────────────────────────────────────

    private SecretKey getSigningKey() {
        // Explicit UTF-8 encoding — removes DM_DEFAULT_ENCODING SpotBugs warning
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Token Generation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generate a signed JWT embedding username (subject) and role (custom claim).
     * Called by AuthService after successful login or registration.
     *
     * @param username the authenticated user's username
     * @param role     the user's role (e.g. "USER", "ADMIN")
     * @return compact signed JWT string
     */
    public String generateToken(String username, String role) {
        return Jwts.builder()
                .setSubject(username)
                .claim(ROLE_CLAIM, role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Token Parsing
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Extract the username (JWT subject) from a token.
     * Called by JwtAuthenticationFilter to populate the SecurityContext.
     */
    public String getUsernameFromToken(String token) {
        return getClaims(token).getSubject();
    }

    /**
     * Extract the role claim from a token.
     * Called by JwtAuthenticationFilter to assign Spring Security authorities.
     */
    public String getRoleFromToken(String token) {
        return getClaims(token).get(ROLE_CLAIM, String.class);
    }

    /**
     * Return token expiry duration in seconds (for the LoginResponse body).
     * Called by AuthService to populate the expiresIn field.
     */
    public long getExpirationSeconds() {
        return expirationMs / 1000;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Token Validation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Validate a token's signature and expiry without requiring the username.
     * Called by JwtAuthenticationFilter — the username is extracted afterwards
     * from the token itself, so passing it in again would be redundant.
     *
     * @param token the JWT string from the Authorization header
     * @return true if the token is valid and not expired; false otherwise
     */
    public boolean validateToken(String token) {
        try {
            getClaims(token); // throws if invalid or expired
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: {}", sanitizeLog(e.getMessage()));
        } catch (MalformedJwtException e) {
            log.warn("JWT token malformed: {}", sanitizeLog(e.getMessage()));
        } catch (SignatureException e) {
            log.warn("JWT signature invalid: {}", sanitizeLog(e.getMessage()));
        } catch (UnsupportedJwtException e) {
            log.warn("JWT token unsupported: {}", sanitizeLog(e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("JWT token is null or empty: {}", sanitizeLog(e.getMessage()));
        }
        return false;
    }

    /**
     * Validate a token AND confirm it belongs to the expected username.
     * Kept for backward compatibility — not currently called by the filter
     * (which extracts username from the token itself), but useful for tests
     * or future callers that need cross-checking.
     *
     * @param token    the JWT string
     * @param username the expected subject
     * @return true if valid, not expired, and subject matches username
     */
    public boolean validateToken(String token, String username) {
        return validateToken(token)
                && username != null
                && username.equals(getUsernameFromToken(token));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal
    // ─────────────────────────────────────────────────────────────────────────

    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /** Strips CR and LF to prevent CRLF injection in log messages. */
    private static String sanitizeLog(String value) {
        if (value == null) return "(null)";
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }
}

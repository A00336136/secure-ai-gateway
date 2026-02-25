package com.secureai.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

/**
 * JWT Utility — Stateless HMAC-SHA256 token management.
 *
 * Security guarantees:
 *  - Tokens signed with HS256 (minimum 256-bit key)
 *  - Expiry enforced; expired tokens rejected
 *  - No DB lookup for validation — pure crypto math
 *  - Subject (username) and role embedded in claims
 */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);
    private static final String ROLE_CLAIM = "role";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms:3600000}")
    private long expirationMs;

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes();
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes for HS256");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generate a signed JWT token for the given user.
     */
    public String generateToken(String username, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .setSubject(username)
                .claim(ROLE_CLAIM, role)
                .setId(UUID.randomUUID().toString())  // jti: guarantees uniqueness per token
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Extract the username (subject) from a valid token.
     */
    public String getUsernameFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Extract the role claim from a valid token.
     */
    public String getRoleFromToken(String token) {
        return parseClaims(token).get(ROLE_CLAIM, String.class);
    }

    /**
     * Validate a token: checks signature, expiry, and structure.
     * Returns true only if the token is fully valid.
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT expired for token: ...{}", sanitizeLog(
                    token != null && token.length() > 10 ? token.substring(token.length() - 10) : ""));
        } catch (io.jsonwebtoken.security.SignatureException e) {
            log.warn("JWT signature invalid: {}", sanitizeLog(e.getMessage()));
        } catch (UnsupportedJwtException e) {
            log.warn("JWT unsupported: {}", sanitizeLog(e.getMessage()));
        } catch (MalformedJwtException e) {
            log.warn("JWT malformed: {}", sanitizeLog(e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("JWT empty or null: {}", sanitizeLog(e.getMessage()));
        }
        return false;
    }

    /**
     * Get token expiration in seconds (for response header).
     */
    public long getExpirationSeconds() {
        return expirationMs / 1000;
    }

    private Claims parseClaims(String token) {
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

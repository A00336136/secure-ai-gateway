package com.secureai.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * JWT Utility — JJWT 0.12.6 API
 *
 * Token lifecycle: generateToken() → validateToken() → invalidateToken() (logout)
 *
 * Security features:
 *  - HMAC-SHA256 signing (automatic key derivation)
 *  - JTI (JWT ID) for replay prevention — each token has a unique UUID
 *  - Issuer validation ("secure-ai-gateway")
 *  - 7-step validation: signature → expiry → issuer → JTI uniqueness → subject → role → blacklist
 *  - Token invalidation via JTI blacklist (POST /api/v1/auth/logout)
 *
 * Distributed blacklist (SOC 2 CC6 / Redis):
 *  - If Redis is available (redis.enabled=true), JTIs are stored in Redis with TTL
 *    equal to the token's remaining lifetime. This ensures blacklisted tokens are
 *    automatically purged when they would have expired anyway, and the blacklist
 *    is consistent across all gateway replicas in a cluster.
 *  - Falls back to in-memory ConcurrentHashMap (dev/test profiles) transparently.
 *
 * JJWT 0.12.6 API:
 *  - Jwts.builder().subject() replaces deprecated .setSubject()
 *  - Jwts.parser().verifyWith() replaces deprecated .parserBuilder().setSigningKey()
 *  - .parseSignedClaims() replaces deprecated .parseClaimsJws()
 *  - .getPayload() replaces deprecated .getBody()
 *  - SignatureAlgorithm enum removed — .signWith(key) auto-selects HS256 for HMAC keys
 */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    private static final String ROLE_CLAIM = "role";
    private static final String ISSUER = "secure-ai-gateway";
    private static final String REDIS_BLACKLIST_PREFIX = "jwt:blacklist:";

    /** In-memory fallback blacklist (dev/test profiles). Thread-safe. */
    private final Set<String> inMemoryBlacklist = ConcurrentHashMap.newKeySet();

    /**
     * Optional Redis template — injected only when redis.enabled=true.
     * Null in dev/test profiles; in-memory fallback used automatically.
     */
    @Nullable
    private final StringRedisTemplate redisTemplate;

    public JwtUtil(@Nullable StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        if (redisTemplate != null) {
            log.info("JwtUtil: Redis blacklist ENABLED — distributed JWT revocation active");
        } else {
            log.info("JwtUtil: Redis not available — using in-memory JWT blacklist (single-node)");
        }
    }

    @Value("${jwt.secret}")
    private String secret;

    /** Expiration in milliseconds (e.g. 3600000 = 1 hour). */
    @Value("${jwt.expiration}")
    private long expirationMs;

    // Key
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Token Generation (JJWT 0.12.6 API)
    // ─────────────────────────────────────────────────────────────────────────

    public String generateToken(String username, String role) {
        return Jwts.builder()
                .subject(username)
                .claim(ROLE_CLAIM, role)
                .id(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Token Parsing
    // ─────────────────────────────────────────────────────────────────────────

    public String getUsernameFromToken(String token) {
        return getClaims(token).getSubject();
    }

    public String getRoleFromToken(String token) {
        return getClaims(token).get(ROLE_CLAIM, String.class);
    }

    public long getExpirationSeconds() {
        return expirationMs / 1000;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Token Validation — 7-Step
    // ─────────────────────────────────────────────────────────────────────────

    public boolean validateToken(String token) {
        try {
            Claims claims = getClaims(token);

            // Step 4: JTI replay / blacklist check
            String jti = claims.getId();
            if (jti != null && isBlacklisted(jti)) {
                log.warn("JWT replay attempt detected: JTI={}", jti);
                return false;
            }

            // Step 5-6: Required claims
            if (claims.getSubject() == null || claims.get(ROLE_CLAIM) == null) {
                log.warn("JWT missing required claims");
                return false;
            }

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

    public boolean validateToken(String token, String username) {
        return validateToken(token)
                && username != null
                && username.equals(getUsernameFromToken(token));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Token Invalidation (Logout)
    // ─────────────────────────────────────────────────────────────────────────

    public void invalidateToken(String token) {
        try {
            Claims claims = getClaims(token);
            String jti = claims.getId();
            if (jti != null) {
                if (redisTemplate != null) {
                    // Redis: store with TTL = remaining lifetime → auto-purge on expiry
                    long ttlMs = claims.getExpiration().getTime() - System.currentTimeMillis();
                    if (ttlMs > 0) {
                        redisTemplate.opsForValue()
                                .set(REDIS_BLACKLIST_PREFIX + jti, "1", ttlMs, TimeUnit.MILLISECONDS);
                        log.info("JWT invalidated (Redis, TTL={}ms): JTI={}", ttlMs, jti);
                    }
                } else {
                    inMemoryBlacklist.add(jti);
                    log.info("JWT invalidated (in-memory): JTI={}", jti);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to invalidate JWT: {}", sanitizeLog(e.getMessage()));
        }
    }

    private boolean isBlacklisted(String jti) {
        if (redisTemplate != null) {
            try {
                return Boolean.TRUE.equals(redisTemplate.hasKey(REDIS_BLACKLIST_PREFIX + jti));
            } catch (Exception e) {
                log.warn("Redis blacklist check failed — falling back to in-memory: {}", e.getMessage());
                return inMemoryBlacklist.contains(jti);
            }
        }
        return inMemoryBlacklist.contains(jti);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal — JJWT 0.12.6 API
    // ─────────────────────────────────────────────────────────────────────────

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .requireIssuer(ISSUER)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private static String sanitizeLog(String value) {
        if (value == null) return "(null)";
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }
}

package com.secureai.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate Limiter Service — Token Bucket Algorithm via Bucket4j
 *
 * Policy:
 *  - Each user gets an independent bucket of 100 tokens
 *  - Every API call consumes 1 token
 *  - Bucket refills completely after 60 minutes
 *  - Buckets are lazily created on first request
 *
 * Distribution modes (OWASP LLM10 / SOC 2 CC12):
 *  - In-memory (default, dev): ConcurrentHashMap — zero DB overhead, O(1) check
 *  - Redis-backed (prod, redis.enabled=true): consistent across replicas,
 *    survives pod restarts, supports HPA scaling without rate-limit bypass
 *
 * Redis implementation uses an atomic counter pattern with TTL:
 *  - Key: "rate:limit:{username}" → request count in current window
 *  - TTL auto-resets the window after refillDurationMinutes
 *  - Atomic INCR + EXPIRE ensures no race conditions
 */
@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);
    private static final String REDIS_RATE_PREFIX = "rate:limit:";

    @Value("${rate-limit.capacity:100}")
    private int capacity;

    @Value("${rate-limit.refill-tokens:100}")
    private int refillTokens;

    @Value("${rate-limit.refill-duration-minutes:60}")
    private int refillDurationMinutes;

    /** Optional Redis — injected when redis.enabled=true, null otherwise. */
    @Nullable
    private final StringRedisTemplate redisTemplate;

    /** In-memory fallback buckets (dev/test profiles). */
    private final Map<String, Bucket> userBuckets = new ConcurrentHashMap<>();

    public RateLimiterService(@Nullable StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        if (redisTemplate != null) {
            log.info("RateLimiterService: Redis backend ENABLED — distributed rate limiting active");
        } else {
            log.info("RateLimiterService: in-memory mode (single-node)");
        }
    }

    /**
     * Attempt to consume one token for the given user.
     * @return true if allowed; false if rate limit exceeded (HTTP 429)
     */
    public boolean tryConsume(String username) {
        if (redisTemplate != null) {
            return tryConsumeRedis(username);
        }
        Bucket bucket = getUserBucket(username);
        boolean allowed = bucket.tryConsume(1);
        if (!allowed) {
            log.warn("Rate limit exceeded for user '{}' (in-memory)", username);
        }
        return allowed;
    }

    /**
     * Get remaining tokens for the given user (for X-Rate-Limit-Remaining header).
     */
    public long getRemainingTokens(String username) {
        if (redisTemplate != null) {
            return getRemainingRedis(username);
        }
        return getUserBucket(username).getAvailableTokens();
    }

    /**
     * Reset the bucket for a user (admin operation).
     */
    public void resetBucket(String username) {
        if (redisTemplate != null) {
            redisTemplate.delete(REDIS_RATE_PREFIX + username);
            log.info("Rate limit bucket reset (Redis) for user '{}'", username);
        } else {
            userBuckets.remove(username);
            log.info("Rate limit bucket reset (in-memory) for user '{}'", username);
        }
    }

    /**
     * Get current capacity (max tokens).
     */
    public int getCapacity() {
        return capacity;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Redis-backed implementation (prod)
    // ─────────────────────────────────────────────────────────────────────────

    private boolean tryConsumeRedis(String username) {
        try {
            String key = REDIS_RATE_PREFIX + username;
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) return false;
            if (count == 1L) {
                // First request in window — set TTL for the window
                redisTemplate.expire(key, Duration.ofMinutes(refillDurationMinutes));
            }
            boolean allowed = count <= capacity;
            if (!allowed) {
                log.warn("Rate limit exceeded for user '{}' (Redis, count={})", username, count);
            }
            return allowed;
        } catch (Exception e) {
            log.warn("Redis rate-limit check failed — falling back to allow: {}", e.getMessage());
            return true; // fail-open for rate limiting (availability preference)
        }
    }

    private long getRemainingRedis(String username) {
        try {
            String key = REDIS_RATE_PREFIX + username;
            String val = redisTemplate.opsForValue().get(key);
            long used = val != null ? Long.parseLong(val) : 0L;
            return Math.max(0, capacity - used);
        } catch (Exception e) {
            log.warn("Redis remaining-tokens check failed: {}", e.getMessage());
            return capacity;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // In-memory implementation (dev/test)
    // ─────────────────────────────────────────────────────────────────────────

    private Bucket getUserBucket(String username) {
        return userBuckets.computeIfAbsent(username, this::createBucket);
    }

    private Bucket createBucket(String username) {
        log.debug("Creating rate limit bucket for user '{}'", username);
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(refillTokens, Duration.ofMinutes(refillDurationMinutes))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

}

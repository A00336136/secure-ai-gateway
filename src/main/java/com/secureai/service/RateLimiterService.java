package com.secureai.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate Limiter Service — Token Bucket Algorithm via Bucket4j
 *
 * Policy:
 *  - Each user gets an independent bucket of 100 tokens
 *  - Every API call consumes 1 token
 *  - Bucket refills completely after 60 minutes
 *  - Buckets are lazily created on first request
 *  - In-memory — zero DB overhead, O(1) check
 *
 * Scale note: For multi-node deployments, replace ConcurrentHashMap
 *  with Bucket4j Redis or Hazelcast backend (config only, no code change).
 */
@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    @Value("${rate-limit.capacity:100}")
    private int capacity;

    @Value("${rate-limit.refill-tokens:100}")
    private int refillTokens;

    @Value("${rate-limit.refill-duration-minutes:60}")
    private int refillDurationMinutes;

    // One bucket per user (username as key)
    private final Map<String, Bucket> userBuckets = new ConcurrentHashMap<>();

    /**
     * Attempt to consume one token for the given user.
     * @return true if allowed; false if rate limit exceeded (HTTP 429)
     */
    public boolean tryConsume(String username) {
        Bucket bucket = getUserBucket(username);
        boolean allowed = bucket.tryConsume(1);
        if (!allowed) {
            log.warn("Rate limit exceeded for user '{}'", sanitizeLog(username));
        }
        return allowed;
    }

    /**
     * Get remaining tokens for the given user (for X-Rate-Limit-Remaining header).
     */
    public long getRemainingTokens(String username) {
        return getUserBucket(username).getAvailableTokens();
    }

    /**
     * Reset the bucket for a user (admin operation).
     */
    public void resetBucket(String username) {
        userBuckets.remove(username);
        log.info("Rate limit bucket reset for user '{}'", sanitizeLog(username));
    }

    /**
     * Get current capacity (max tokens).
     */
    public int getCapacity() {
        return capacity;
    }

    // ─────────────────────────────────────────────────────────────────────────

    private Bucket getUserBucket(String username) {
        return userBuckets.computeIfAbsent(username, this::createBucket);
    }

    private Bucket createBucket(String username) {
        log.debug("Creating rate limit bucket for user '{}'", sanitizeLog(username));
        Bandwidth limit = Bandwidth.classic(
                capacity,
                Refill.greedy(refillTokens, Duration.ofMinutes(refillDurationMinutes))
        );
        return Bucket.builder().addLimit(limit).build();
    }

    /** Strips CR and LF to prevent CRLF injection in log messages. */
    private static String sanitizeLog(String value) {
        if (value == null) return "(null)";
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }
}

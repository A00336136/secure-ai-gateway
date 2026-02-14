package com.secureai.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for rate limiting requests using the token bucket algorithm.
 * Implements per-user rate limiting to prevent abuse.
 */
@Slf4j
@Service
public class RateLimitService {

    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    @Value("${rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${rate-limit.capacity:100}")
    private long capacity;

    @Value("${rate-limit.refill-tokens:10}")
    private long refillTokens;

    @Value("${rate-limit.refill-period-seconds:60}")
    private long refillPeriodSeconds;

    /**
     * Check if a request from the given user is allowed.
     *
     * @param username the username making the request
     * @return true if request is allowed, false if rate limit exceeded
     */
    public boolean isAllowed(String username) {
        if (!enabled) {
            return true;
        }

        Bucket bucket = resolveBucket(username);
        boolean allowed = bucket.tryConsume(1);

        if (!allowed) {
            log.warn("Rate limit exceeded for user: {}", username);
        } else {
            log.debug("Request allowed for user: {}. Remaining tokens: {}", 
                    username, bucket.getAvailableTokens());
        }

        return allowed;
    }

    /**
     * Get the bucket for a specific user, creating it if it doesn't exist.
     *
     * @param username the username
     * @return the bucket for this user
     */
    private Bucket resolveBucket(String username) {
        return cache.computeIfAbsent(username, k -> createNewBucket());
    }

    /**
     * Create a new bucket with configured limits.
     *
     * @return a new bucket instance
     */
    private Bucket createNewBucket() {
        Bandwidth limit = Bandwidth.classic(
                capacity,
                Refill.intervally(refillTokens, Duration.ofSeconds(refillPeriodSeconds))
        );
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Get remaining tokens for a user.
     *
     * @param username the username
     * @return the number of remaining tokens
     */
    public long getRemainingTokens(String username) {
        if (!enabled) {
            return capacity;
        }

        Bucket bucket = resolveBucket(username);
        return bucket.getAvailableTokens();
    }

    /**
     * Reset the rate limit for a specific user (admin function).
     *
     * @param username the username to reset
     */
    public void resetLimit(String username) {
        cache.remove(username);
        log.info("Rate limit reset for user: {}", username);
    }
}

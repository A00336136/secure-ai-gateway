package com.secureai.performance;

import com.secureai.service.RateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance Test — Test Pyramid Layer 3
 *
 * Measures Rate Limiter (Bucket4j) performance under concurrent load:
 *  - Single-user bucket throughput
 *  - Multi-user concurrent bucket creation
 *  - Token depletion accuracy under contention
 */
@DisplayName("Performance Tests — Rate Limiter Throughput")
class RateLimiterPerfTest {

    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        rateLimiterService = new RateLimiterService();
        try {
            var capacityField = RateLimiterService.class.getDeclaredField("capacity");
            capacityField.setAccessible(true);
            capacityField.set(rateLimiterService, 100L);
            var refillTokensField = RateLimiterService.class.getDeclaredField("refillTokens");
            refillTokensField.setAccessible(true);
            refillTokensField.set(rateLimiterService, 100L);
            var refillDurationField = RateLimiterService.class.getDeclaredField("refillDuration");
            refillDurationField.setAccessible(true);
            refillDurationField.set(rateLimiterService, 60L);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set rate limiter fields", e);
        }
    }

    @Test
    @DisplayName("Rate limit check: 10,000 operations under 1 second")
    void singleUserThroughput() {
        int checkCount = 10000;
        Instant start = Instant.now();

        int allowed = 0;
        int denied = 0;
        for (int i = 0; i < checkCount; i++) {
            if (rateLimiterService.tryConsume("perf-user")) {
                allowed++;
            } else {
                denied++;
            }
        }

        Duration elapsed = Duration.between(start, Instant.now());
        double opsPerSecond = checkCount / (elapsed.toMillis() / 1000.0);

        System.out.printf("[PERF] Rate Limiter: %d checks in %d ms (%.0f ops/sec), allowed=%d, denied=%d%n",
                checkCount, elapsed.toMillis(), opsPerSecond, allowed, denied);

        assertThat(elapsed).isLessThan(Duration.ofSeconds(1));
        assertThat(allowed).isEqualTo(100); // capacity = 100
        assertThat(denied).isEqualTo(checkCount - 100);
    }

    @Test
    @DisplayName("Concurrent: 100 users each making 50 requests")
    void multiUserConcurrentLoad() throws Exception {
        int userCount = 100;
        int requestsPerUser = 50;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(userCount);
        AtomicInteger totalAllowed = new AtomicInteger(0);
        AtomicInteger totalDenied = new AtomicInteger(0);

        Instant start = Instant.now();

        for (int u = 0; u < userCount; u++) {
            final String username = "user-" + u;
            executor.submit(() -> {
                try {
                    for (int r = 0; r < requestsPerUser; r++) {
                        if (rateLimiterService.tryConsume(username)) {
                            totalAllowed.incrementAndGet();
                        } else {
                            totalDenied.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        Duration elapsed = Duration.between(start, Instant.now());
        int totalOps = userCount * requestsPerUser;
        double opsPerSecond = totalOps / (elapsed.toMillis() / 1000.0);

        System.out.printf("[PERF] Multi-User Rate Limit: %d users x %d req = %d ops in %d ms (%.0f ops/sec)%n",
                userCount, requestsPerUser, totalOps, elapsed.toMillis(), opsPerSecond);
        System.out.printf("[PERF] Allowed: %d, Denied: %d%n", totalAllowed.get(), totalDenied.get());

        assertThat(elapsed).isLessThan(Duration.ofSeconds(10));
        // Each user gets 50 requests but capacity is 100, so all should be allowed
        assertThat(totalAllowed.get()).isEqualTo(totalOps);
    }
}

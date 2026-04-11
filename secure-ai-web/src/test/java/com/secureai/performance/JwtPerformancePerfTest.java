package com.secureai.performance;

import com.secureai.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance Test — Test Pyramid Layer 3
 *
 * Measures throughput and latency of security-critical operations:
 *  - JWT token generation rate
 *  - JWT token validation rate
 *  - Concurrent token operations under load
 *
 * Naming convention: *PerfTest.java (excluded from Surefire, manual execution)
 * Run with: mvn test -Dtest=JwtPerformancePerfTest
 */
@DisplayName("Performance Tests — JWT Operations Throughput")
class JwtPerformancePerfTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(null);
        // Use reflection or test constructor to set secret
        try {
            var secretField = JwtUtil.class.getDeclaredField("secret");
            secretField.setAccessible(true);
            secretField.set(jwtUtil, "performance-test-secret-key-minimum-32-characters-long");
            var expirationField = JwtUtil.class.getDeclaredField("expiration");
            expirationField.setAccessible(true);
            expirationField.set(jwtUtil, 3600000L);
            // Call @PostConstruct init manually
            var initMethod = JwtUtil.class.getDeclaredMethod("init");
            initMethod.setAccessible(true);
            initMethod.invoke(jwtUtil);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize JwtUtil for perf test", e);
        }
    }

    @Test
    @DisplayName("JWT generation: 1000 tokens under 2 seconds")
    void jwtGenerationThroughput() {
        int tokenCount = 1000;
        Instant start = Instant.now();

        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < tokenCount; i++) {
            tokens.add(jwtUtil.generateToken("user-" + i, "USER"));
        }

        Duration elapsed = Duration.between(start, Instant.now());
        double tokensPerSecond = tokenCount / (elapsed.toMillis() / 1000.0);

        System.out.printf("[PERF] JWT Generation: %d tokens in %d ms (%.0f tokens/sec)%n",
                tokenCount, elapsed.toMillis(), tokensPerSecond);

        assertThat(elapsed).isLessThan(Duration.ofSeconds(2));
        assertThat(tokens).hasSize(tokenCount);
        assertThat(tokensPerSecond).isGreaterThan(500);
    }

    @Test
    @DisplayName("JWT validation: 1000 validations under 2 seconds")
    void jwtValidationThroughput() {
        String token = jwtUtil.generateToken("perfuser", "USER");
        int validationCount = 1000;
        Instant start = Instant.now();

        for (int i = 0; i < validationCount; i++) {
            assertThat(jwtUtil.validateToken(token)).isTrue();
        }

        Duration elapsed = Duration.between(start, Instant.now());
        double validationsPerSecond = validationCount / (elapsed.toMillis() / 1000.0);

        System.out.printf("[PERF] JWT Validation: %d validations in %d ms (%.0f ops/sec)%n",
                validationCount, elapsed.toMillis(), validationsPerSecond);

        assertThat(elapsed).isLessThan(Duration.ofSeconds(2));
        assertThat(validationsPerSecond).isGreaterThan(500);
    }

    @Test
    @DisplayName("Concurrent JWT operations: 50 threads, 100 tokens each")
    void concurrentJwtOperations() throws Exception {
        int threadCount = 50;
        int tokensPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        ConcurrentLinkedQueue<String> allTokens = new ConcurrentLinkedQueue<>();

        Instant start = Instant.now();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < tokensPerThread; i++) {
                        String token = jwtUtil.generateToken("thread-" + threadId + "-user-" + i, "USER");
                        assertThat(jwtUtil.validateToken(token)).isTrue();
                        allTokens.add(token);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        Duration elapsed = Duration.between(start, Instant.now());
        int totalOps = threadCount * tokensPerThread * 2; // generate + validate
        double opsPerSecond = totalOps / (elapsed.toMillis() / 1000.0);

        System.out.printf("[PERF] Concurrent JWT: %d threads x %d tokens = %d total ops in %d ms (%.0f ops/sec)%n",
                threadCount, tokensPerThread, totalOps, elapsed.toMillis(), opsPerSecond);

        assertThat(allTokens).hasSize(threadCount * tokensPerThread);
        assertThat(elapsed).isLessThan(Duration.ofSeconds(30));
    }
}

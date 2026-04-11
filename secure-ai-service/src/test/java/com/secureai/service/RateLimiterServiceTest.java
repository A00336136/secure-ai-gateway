package com.secureai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimiterService Unit Tests")
class RateLimiterServiceTest {

    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        rateLimiterService = new RateLimiterService(null); // null = in-memory mode (no Redis in tests)
        org.springframework.test.util.ReflectionTestUtils.setField(rateLimiterService, "capacity", 100);
        org.springframework.test.util.ReflectionTestUtils.setField(rateLimiterService, "refillTokens", 100);
        org.springframework.test.util.ReflectionTestUtils.setField(rateLimiterService, "refillDurationMinutes", 60);
    }

    @Test
    @DisplayName("tryConsume should allow requests within capacity and block after limit")
    void tryConsumeShouldRespectLimits() {
        String user = "testuser";
        
        // Capacity is 100 per hour by default
        for (int i = 0; i < 100; i++) {
            assertTrue(rateLimiterService.tryConsume(user), "Request " + (i+1) + " should be allowed");
        }
        
        assertFalse(rateLimiterService.tryConsume(user), "Request 101 should be blocked");
        assertEquals(0, rateLimiterService.getRemainingTokens(user));
    }

    @Test
    @DisplayName("resetBucket should remove user bucket")
    void resetBucketShouldRemoveBucket() {
        rateLimiterService.tryConsume("user1");
        rateLimiterService.resetBucket("user1");
        
        // Next call should create a fresh bucket
        assertEquals(100, rateLimiterService.getRemainingTokens("user1"));
    }

    @Test
    @DisplayName("getCapacity should return configured capacity")
    void getCapacityShouldReturnCapacity() {
        assertEquals(100, rateLimiterService.getCapacity());
    }

    @Test
    @DisplayName("tryConsume should handle null username gracefully")
    void tryConsumeShouldHandleNullUsername() {
        // ConcurrentHashMap does not allow null keys, so tryConsume(null) should throw NPE
        assertThrows(NullPointerException.class, () -> rateLimiterService.tryConsume(null));
    }

    @Test
    @DisplayName("getRemainingTokens should return capacity for new user")
    void getRemainingTokensShouldReturnCapacityForNewUser() {
        assertEquals(100, rateLimiterService.getRemainingTokens("newuser"));
    }

    @Test
    @DisplayName("getRemainingTokens should decrease after consumption")
    void getRemainingTokensShouldDecreaseAfterConsumption() {
        rateLimiterService.tryConsume("decreaseUser");
        assertEquals(99, rateLimiterService.getRemainingTokens("decreaseUser"));
    }

    @Test
    @DisplayName("Different users should have independent buckets")
    void differentUsersShouldHaveIndependentBuckets() {
        for (int i = 0; i < 50; i++) {
            rateLimiterService.tryConsume("userA");
        }
        assertEquals(50, rateLimiterService.getRemainingTokens("userA"));
        assertEquals(100, rateLimiterService.getRemainingTokens("userB"));
    }

    @Test
    @DisplayName("resetBucket should restore full capacity")
    void resetBucketShouldRestoreFullCapacity() {
        for (int i = 0; i < 100; i++) {
            rateLimiterService.tryConsume("exhaustedUser");
        }
        assertFalse(rateLimiterService.tryConsume("exhaustedUser"));
        rateLimiterService.resetBucket("exhaustedUser");
        assertTrue(rateLimiterService.tryConsume("exhaustedUser"));
        assertEquals(99, rateLimiterService.getRemainingTokens("exhaustedUser"));
    }

    @Test
    @DisplayName("Should create bucket with custom capacity")
    void shouldCreateBucketWithCustomCapacity() {
        org.springframework.test.util.ReflectionTestUtils.setField(rateLimiterService, "capacity", 5);
        org.springframework.test.util.ReflectionTestUtils.setField(rateLimiterService, "refillTokens", 5);

        // Reset to force new bucket creation with custom capacity
        rateLimiterService.resetBucket("customUser");
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiterService.tryConsume("customUser"));
        }
        assertFalse(rateLimiterService.tryConsume("customUser"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Redis-backed mode tests
    // Tests all branches in tryConsumeRedis, getRemainingRedis, and Redis resetBucket
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Redis-backed mode (StringRedisTemplate injected)")
    @MockitoSettings(strictness = Strictness.LENIENT)
    class RedisModeTests {

        @SuppressWarnings("unchecked")
        private ValueOperations<String, String> valueOps;
        private StringRedisTemplate             redisTemplate;
        private RateLimiterService              redisService;

        @BeforeEach
        @SuppressWarnings("unchecked")
        void setUpRedis() {
            redisTemplate = mock(StringRedisTemplate.class);
            valueOps      = mock(ValueOperations.class);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);

            redisService = new RateLimiterService(redisTemplate);
            org.springframework.test.util.ReflectionTestUtils.setField(redisService, "capacity", 100);
            org.springframework.test.util.ReflectionTestUtils.setField(redisService, "refillTokens", 100);
            org.springframework.test.util.ReflectionTestUtils.setField(redisService, "refillDurationMinutes", 60);
        }

        // ── tryConsumeRedis branches ──────────────────────────────────────────

        @Test
        @DisplayName("Redis: first request (count=1) is allowed and TTL is set on key")
        void tryConsumeFirstRequestAllowedAndSetsTTL() {
            when(valueOps.increment("rate:limit:alice")).thenReturn(1L);
            assertTrue(redisService.tryConsume("alice"));
            verify(redisTemplate).expire(eq("rate:limit:alice"), any(Duration.class));
        }

        @Test
        @DisplayName("Redis: subsequent request (count=50) is allowed; TTL not re-set")
        void tryConsumeSubsequentRequestAllowedNoTTL() {
            when(valueOps.increment("rate:limit:alice")).thenReturn(50L);
            assertTrue(redisService.tryConsume("alice"));
            verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("Redis: request at exactly capacity (count=100) is allowed")
        void tryConsumeAtCapacityAllowed() {
            when(valueOps.increment("rate:limit:alice")).thenReturn(100L);
            assertTrue(redisService.tryConsume("alice"));
        }

        @Test
        @DisplayName("Redis: request exceeding capacity (count=101) is blocked")
        void tryConsumeOverCapacityBlocked() {
            when(valueOps.increment("rate:limit:alice")).thenReturn(101L);
            assertFalse(redisService.tryConsume("alice"));
        }

        @Test
        @DisplayName("Redis: null increment return from Redis → blocked (null branch)")
        void tryConsumeNullIncrementBlocked() {
            when(valueOps.increment(anyString())).thenReturn(null);
            assertFalse(redisService.tryConsume("alice"));
        }

        @Test
        @DisplayName("Redis: exception during increment → fail-open (returns true)")
        void tryConsumeRedisExceptionFailOpen() {
            when(valueOps.increment(anyString())).thenThrow(new RuntimeException("Redis unavailable"));
            assertTrue(redisService.tryConsume("alice"), "Should fail-open on Redis exception");
        }

        // ── getRemainingRedis branches ────────────────────────────────────────

        @Test
        @DisplayName("Redis: getRemainingTokens returns capacity minus current usage")
        void getRemainingTokensReturnsCapacityMinusUsed() {
            when(valueOps.get("rate:limit:alice")).thenReturn("30");
            assertEquals(70L, redisService.getRemainingTokens("alice"));
        }

        @Test
        @DisplayName("Redis: getRemainingTokens returns full capacity when key absent (null val branch)")
        void getRemainingTokensReturnsCapacityWhenKeyAbsent() {
            when(valueOps.get(anyString())).thenReturn(null);
            assertEquals(100L, redisService.getRemainingTokens("alice"));
        }

        @Test
        @DisplayName("Redis: getRemainingTokens returns capacity on exception (fail-safe)")
        void getRemainingTokensReturnsCapacityOnException() {
            when(valueOps.get(anyString())).thenThrow(new RuntimeException("Redis down"));
            assertEquals(100L, redisService.getRemainingTokens("alice"));
        }

        @Test
        @DisplayName("Redis: getRemainingTokens returns 0 when bucket fully exhausted (usage >= capacity)")
        void getRemainingTokensReturnsZeroWhenExhausted() {
            when(valueOps.get("rate:limit:alice")).thenReturn("100");
            assertEquals(0L, redisService.getRemainingTokens("alice"));
        }

        // ── resetBucket with Redis ────────────────────────────────────────────

        @Test
        @DisplayName("Redis: resetBucket deletes the Redis key for the user")
        void resetBucketDeletesRedisKey() {
            redisService.resetBucket("alice");
            verify(redisTemplate).delete("rate:limit:alice");
        }

        @Test
        @DisplayName("Redis: getCapacity returns configured capacity")
        void getCapacityReturnsCorrectValue() {
            assertEquals(100, redisService.getCapacity());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Direct private-method null-guard tests (covers dead-code null branches in private methods)
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Private method null-guard branches (via reflection)")
    @MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    class PrivateMethodNullGuardTests {

        @Test
        @DisplayName("tryConsumeRedis with null redisTemplate returns true (fail-open null guard)")
        void tryConsumeRedisWithNullTemplateReturnsTrue() throws Exception {
            // Create service with non-null template (passes constructor), then null it out
            StringRedisTemplate template = mock(StringRedisTemplate.class);
            RateLimiterService svc = new RateLimiterService(template);
            org.springframework.test.util.ReflectionTestUtils.setField(svc, "redisTemplate", null);
            org.springframework.test.util.ReflectionTestUtils.setField(svc, "capacity", 100);

            var method = RateLimiterService.class.getDeclaredMethod("tryConsumeRedis", String.class);
            method.setAccessible(true);
            Boolean result = (Boolean) method.invoke(svc, "user");
            assertTrue(result, "tryConsumeRedis with null template should fail-open (return true)");
        }

        @Test
        @DisplayName("getRemainingRedis with null redisTemplate returns capacity (null guard)")
        void getRemainingRedisWithNullTemplateReturnsCapacity() throws Exception {
            StringRedisTemplate template = mock(StringRedisTemplate.class);
            RateLimiterService svc = new RateLimiterService(template);
            org.springframework.test.util.ReflectionTestUtils.setField(svc, "redisTemplate", null);
            org.springframework.test.util.ReflectionTestUtils.setField(svc, "capacity", 100);

            var method = RateLimiterService.class.getDeclaredMethod("getRemainingRedis", String.class);
            method.setAccessible(true);
            Long result = (Long) method.invoke(svc, "user");
            assertEquals(100L, result, "getRemainingRedis with null template should return capacity");
        }
    }
}

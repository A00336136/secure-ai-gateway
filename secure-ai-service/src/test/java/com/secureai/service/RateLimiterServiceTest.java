package com.secureai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimiterService Unit Tests")
class RateLimiterServiceTest {

    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        rateLimiterService = new RateLimiterService();
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
}

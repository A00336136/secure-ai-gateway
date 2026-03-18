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
    @DisplayName("getRemainingTokens should return capacity for new users")
    void getRemainingTokensNewUser() {
        assertEquals(100, rateLimiterService.getRemainingTokens("newuser"));
    }
}

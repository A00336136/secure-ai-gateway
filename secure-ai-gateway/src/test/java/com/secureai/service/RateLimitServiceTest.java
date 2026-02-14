package com.secureai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RateLimitService class.
 * Tests rate limiting functionality.
 */
class RateLimitServiceTest {

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService();
        ReflectionTestUtils.setField(rateLimitService, "enabled", true);
        ReflectionTestUtils.setField(rateLimitService, "capacity", 10L);
        ReflectionTestUtils.setField(rateLimitService, "refillTokens", 2L);
        ReflectionTestUtils.setField(rateLimitService, "refillPeriodSeconds", 60L);
    }

    @Test
    void testIsAllowed_FirstRequest_ReturnsTrue() {
        // Given
        String username = "testuser";

        // When
        boolean allowed = rateLimitService.isAllowed(username);

        // Then
        assertTrue(allowed);
    }

    @Test
    void testIsAllowed_WithinLimit_ReturnsTrue() {
        // Given
        String username = "testuser";

        // When & Then
        for (int i = 0; i < 10; i++) {
            assertTrue(rateLimitService.isAllowed(username), 
                    "Request " + (i + 1) + " should be allowed");
        }
    }

    @Test
    void testIsAllowed_ExceedsLimit_ReturnsFalse() {
        // Given
        String username = "testuser";

        // Consume all tokens
        for (int i = 0; i < 10; i++) {
            rateLimitService.isAllowed(username);
        }

        // When - Try one more request
        boolean allowed = rateLimitService.isAllowed(username);

        // Then
        assertFalse(allowed);
    }

    @Test
    void testIsAllowed_DifferentUsers_IndependentLimits() {
        // Given
        String user1 = "user1";
        String user2 = "user2";

        // When - Exhaust user1's limit
        for (int i = 0; i < 10; i++) {
            rateLimitService.isAllowed(user1);
        }

        // Then - user2 should still be allowed
        assertTrue(rateLimitService.isAllowed(user2));
        assertFalse(rateLimitService.isAllowed(user1));
    }

    @Test
    void testGetRemainingTokens_InitialState_ReturnsCapacity() {
        // Given
        String username = "testuser";

        // When
        long remaining = rateLimitService.getRemainingTokens(username);

        // Then
        assertEquals(10L, remaining);
    }

    @Test
    void testGetRemainingTokens_AfterConsumption_ReturnsCorrectValue() {
        // Given
        String username = "testuser";
        rateLimitService.isAllowed(username);
        rateLimitService.isAllowed(username);

        // When
        long remaining = rateLimitService.getRemainingTokens(username);

        // Then
        assertEquals(8L, remaining);
    }

    @Test
    void testResetLimit_ResetsUserTokens() {
        // Given
        String username = "testuser";
        
        // Consume all tokens
        for (int i = 0; i < 10; i++) {
            rateLimitService.isAllowed(username);
        }
        
        assertFalse(rateLimitService.isAllowed(username));

        // When
        rateLimitService.resetLimit(username);

        // Then
        assertTrue(rateLimitService.isAllowed(username));
        assertEquals(9L, rateLimitService.getRemainingTokens(username));
    }

    @Test
    void testIsAllowed_WhenDisabled_AlwaysReturnsTrue() {
        // Given
        ReflectionTestUtils.setField(rateLimitService, "enabled", false);
        String username = "testuser";

        // When & Then - Should allow unlimited requests
        for (int i = 0; i < 20; i++) {
            assertTrue(rateLimitService.isAllowed(username));
        }
    }

    @Test
    void testGetRemainingTokens_WhenDisabled_ReturnsCapacity() {
        // Given
        ReflectionTestUtils.setField(rateLimitService, "enabled", false);
        String username = "testuser";

        // When
        long remaining = rateLimitService.getRemainingTokens(username);

        // Then
        assertEquals(10L, remaining);
    }
}

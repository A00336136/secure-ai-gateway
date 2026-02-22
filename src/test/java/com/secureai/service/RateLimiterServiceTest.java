package com.secureai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RateLimiterService Tests")
class RateLimiterServiceTest {

    private RateLimiterService service;

    @BeforeEach
    void setUp() {
        service = new RateLimiterService();
        ReflectionTestUtils.setField(service, "capacity", 5);
        ReflectionTestUtils.setField(service, "refillTokens", 5);
        ReflectionTestUtils.setField(service, "refillDurationMinutes", 60);
    }

    @Test
    @DisplayName("First request should be allowed")
    void firstRequestAllowed() {
        assertThat(service.tryConsume("user1")).isTrue();
    }

    @Test
    @DisplayName("Requests within capacity should all be allowed")
    void requestsWithinCapacityAllowed() {
        for (int i = 0; i < 5; i++) {
            assertThat(service.tryConsume("user2")).isTrue();
        }
    }

    @Test
    @DisplayName("Request exceeding capacity should be denied")
    void requestExceedingCapacityDenied() {
        for (int i = 0; i < 5; i++) {
            service.tryConsume("user3");
        }
        assertThat(service.tryConsume("user3")).isFalse();
    }

    @Test
    @DisplayName("Different users should have independent buckets")
    void differentUsersHaveIndependentBuckets() {
        for (int i = 0; i < 5; i++) {
            service.tryConsume("userA");
        }
        // userA exhausted, but userB should still work
        assertThat(service.tryConsume("userA")).isFalse();
        assertThat(service.tryConsume("userB")).isTrue();
    }

    @Test
    @DisplayName("Remaining tokens should decrease with each request")
    void remainingTokensDecrease() {
        long before = service.getRemainingTokens("userC");
        service.tryConsume("userC");
        long after = service.getRemainingTokens("userC");
        assertThat(after).isLessThan(before);
    }

    @Test
    @DisplayName("Reset should restore full capacity")
    void resetRestoresCapacity() {
        for (int i = 0; i < 5; i++) {
            service.tryConsume("userD");
        }
        assertThat(service.tryConsume("userD")).isFalse();
        service.resetBucket("userD");
        assertThat(service.tryConsume("userD")).isTrue();
    }

    @Test
    @DisplayName("Capacity getter should return configured value")
    void capacityShouldReturnConfigured() {
        assertThat(service.getCapacity()).isEqualTo(5);
    }
}

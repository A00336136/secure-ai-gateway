package com.secureai.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;

class UserAuditLogEntityTest {
    @Test
    void testUserEntityBuilderAndGetters() {
        LocalDateTime now = LocalDateTime.now();
        User user = User.builder()
                .id(1L)
                .username("alice")
                .password("pw")
                .email("a@b.com")
                .role("ADMIN")
                .enabled(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
        assertEquals(1L, user.getId());
        assertEquals("alice", user.getUsername());
        assertEquals("pw", user.getPassword());
        assertEquals("a@b.com", user.getEmail());
        assertEquals("ADMIN", user.getRole());
        assertFalse(user.isEnabled());
        assertEquals(now, user.getCreatedAt());
        assertEquals(now, user.getUpdatedAt());
    }

    @Test
    void testAuditLogEntityBuilderAndGetters() {
        LocalDateTime now = LocalDateTime.now();
        AuditLog log = AuditLog.builder()
                .id(2L)
                .username("bob")
                .prompt("p")
                .response("r")
                .model("m")
                .piiDetected(true)
                .rateLimited(true)
                .reactSteps(3)
                .statusCode(200)
                .durationMs(10L)
                .ipAddress("127.0.0.1")
                .createdAt(now)
                .build();
        assertEquals(2L, log.getId());
        assertEquals("bob", log.getUsername());
        assertEquals("p", log.getPrompt());
        assertEquals("r", log.getResponse());
        assertEquals("m", log.getModel());
        assertTrue(log.isPiiDetected());
        assertTrue(log.isRateLimited());
        assertEquals(3, log.getReactSteps());
        assertEquals(200, log.getStatusCode());
        assertEquals(10L, log.getDurationMs());
        assertEquals("127.0.0.1", log.getIpAddress());
        assertEquals(now, log.getCreatedAt());
    }
}

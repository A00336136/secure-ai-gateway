package com.secureai.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration tests for the Secure AI Gateway application.
 * Verifies that the application context loads correctly.
 */
@SpringBootTest
class SecureAiGatewayIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
        assertNotNull(applicationContext, "Application context should not be null");
    }

    @Test
    void allBeansLoad() {
        assertNotNull(applicationContext.getBean("jwtUtil"));
        assertNotNull(applicationContext.getBean("piiRedactionService"));
        assertNotNull(applicationContext.getBean("ollamaService"));
        assertNotNull(applicationContext.getBean("rateLimitService"));
        assertNotNull(applicationContext.getBean("authController"));
        assertNotNull(applicationContext.getBean("askController"));
    }
}

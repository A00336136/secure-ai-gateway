package com.secureai.smoke;

import com.secureai.controller.AskController;
import com.secureai.controller.AuthController;
import com.secureai.controller.AdminController;
import com.secureai.security.JwtUtil;
import com.secureai.service.AuthService;
import com.secureai.service.OllamaClient;
import com.secureai.service.RateLimiterService;
import com.secureai.pii.PiiRedactionService;
import com.secureai.guardrails.GuardrailsOrchestrator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke Test — Test Pyramid Layer 2
 *
 * Verifies that the Spring Boot application context loads successfully
 * and all critical beans are wired. This catches:
 *  - Missing @Component / @Service / @Configuration annotations
 *  - Circular dependencies
 *  - Missing configuration properties
 *  - Bean creation failures
 *
 * Naming convention: *SmokeTest.java (picked up by maven-failsafe-plugin)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Smoke Tests — Application Context & Bean Wiring")
class ApplicationSmokeTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("Application context loads successfully")
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }

    @Test
    @DisplayName("AskController bean is present")
    void askControllerLoads(@Autowired AskController controller) {
        assertThat(controller).isNotNull();
    }

    @Test
    @DisplayName("AuthController bean is present")
    void authControllerLoads(@Autowired AuthController controller) {
        assertThat(controller).isNotNull();
    }

    @Test
    @DisplayName("AdminController bean is present")
    void adminControllerLoads(@Autowired AdminController controller) {
        assertThat(controller).isNotNull();
    }

    @Test
    @DisplayName("JwtUtil security bean is present")
    void jwtUtilLoads(@Autowired JwtUtil jwtUtil) {
        assertThat(jwtUtil).isNotNull();
    }

    @Test
    @DisplayName("AuthService bean is present")
    void authServiceLoads(@Autowired AuthService authService) {
        assertThat(authService).isNotNull();
    }

    @Test
    @DisplayName("RateLimiterService bean is present")
    void rateLimiterLoads(@Autowired RateLimiterService rateLimiter) {
        assertThat(rateLimiter).isNotNull();
    }

    @Test
    @DisplayName("PiiRedactionService bean is present")
    void piiRedactionLoads(@Autowired PiiRedactionService piiService) {
        assertThat(piiService).isNotNull();
    }

    @Test
    @DisplayName("GuardrailsOrchestrator bean is present")
    void guardrailsOrchestratorLoads(@Autowired GuardrailsOrchestrator orchestrator) {
        assertThat(orchestrator).isNotNull();
    }

    @Test
    @DisplayName("OllamaClient bean is present")
    void ollamaClientLoads(@Autowired OllamaClient ollamaClient) {
        assertThat(ollamaClient).isNotNull();
    }
}

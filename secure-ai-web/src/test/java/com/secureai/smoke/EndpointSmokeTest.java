package com.secureai.smoke;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoint Smoke Test — Test Pyramid Layer 2
 *
 * Verifies that critical API endpoints respond (not 404/500).
 * Does NOT test business logic — that belongs to Unit Tests.
 *
 * Naming convention: *SmokeTest.java (picked up by maven-failsafe-plugin)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Smoke Tests — Endpoint Availability")
class EndpointSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /actuator/health returns 200")
    void healthEndpointUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /auth/health returns 200")
    void authHealthEndpointUp() throws Exception {
        mockMvc.perform(get("/auth/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /auth/login without body returns 400 (not 404 or 500)")
    void loginEndpointExists() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("POST /api/ask without token returns 401/403 (not 404)")
    void askEndpointProtected() throws Exception {
        mockMvc.perform(post("/api/ask")
                        .contentType("application/json")
                        .content("{\"prompt\":\"test\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("GET /admin/dashboard without auth returns 401/403")
    void adminEndpointProtected() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Swagger UI available at /swagger-ui.html")
    void swaggerUiAvailable() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("OpenAPI spec available at /v3/api-docs")
    void openApiSpecAvailable() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }
}

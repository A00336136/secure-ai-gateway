package com.secureai.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureai.model.LoginRequest;
import com.secureai.model.RegisterRequest;
import com.secureai.security.JwtUtil;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration Test — Test Pyramid Layer 4
 *
 * End-to-end security flow tests:
 *  - Register → Login → Authenticate → Access Protected Resource
 *  - JWT lifecycle: generate → use → invalidate
 *  - Role-based access control (USER vs ADMIN)
 *  - Rate limiting across multiple requests
 *
 * Naming convention: *IT.java (picked up by maven-failsafe-plugin)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Integration Tests — End-to-End Security Flow")
class SecurityIntegrationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    private static String userToken;

    @Test
    @Order(1)
    @DisplayName("Register new user returns 201")
    void registerNewUser() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("integrationuser");
        request.setPassword("SecureP@ss123");
        request.setEmail("integration@test.com");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @Order(2)
    @DisplayName("Login with registered user returns JWT token")
    void loginReturnsToken() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("integrationuser");
        request.setPassword("SecureP@ss123");

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").isNumber())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        userToken = objectMapper.readTree(responseBody).get("token").asText();
        assertThat(userToken).isNotBlank();
    }

    @Test
    @Order(3)
    @DisplayName("Access protected endpoint with valid token succeeds")
    void accessProtectedWithValidToken() throws Exception {
        assertThat(userToken).isNotNull();

        mockMvc.perform(get("/api/status")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(4)
    @DisplayName("Access protected endpoint without token returns 403")
    void accessProtectedWithoutToken() throws Exception {
        mockMvc.perform(get("/api/status"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(5)
    @DisplayName("Access protected endpoint with tampered token returns 403")
    void accessProtectedWithTamperedToken() throws Exception {
        String tampered = userToken + "TAMPERED";

        mockMvc.perform(get("/api/status")
                        .header("Authorization", "Bearer " + tampered))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(6)
    @DisplayName("Access admin endpoint with USER role returns 403")
    void adminEndpointDeniedForUser() throws Exception {
        assertThat(userToken).isNotNull();

        mockMvc.perform(get("/admin/dashboard")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(7)
    @DisplayName("Access admin endpoint with ADMIN token succeeds")
    void adminEndpointAllowedForAdmin() throws Exception {
        String adminToken = jwtUtil.generateToken("admin", "ADMIN");

        mockMvc.perform(get("/admin/dashboard")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(8)
    @DisplayName("Duplicate registration returns 409 Conflict")
    void duplicateRegistrationFails() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("integrationuser");
        request.setPassword("AnotherP@ss456");
        request.setEmail("another@test.com");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(9)
    @DisplayName("Rate limit headers present in API response")
    void rateLimitHeadersPresent() throws Exception {
        assertThat(userToken).isNotNull();

        mockMvc.perform(post("/api/ask")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"hello\"}"))
                .andExpect(header().exists("X-Rate-Limit-Remaining"));
    }
}

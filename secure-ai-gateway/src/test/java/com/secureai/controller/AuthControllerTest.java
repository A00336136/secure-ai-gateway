package com.secureai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureai.model.LoginRequest;
import com.secureai.model.RegisterRequest;
import com.secureai.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("AuthController Integration Tests")
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Nested
    @DisplayName("POST /auth/login")
    class LoginTests {

        @Test
        @DisplayName("Valid credentials should return 200 with JWT token")
        void validCredentialsShouldReturn200() throws Exception {
            LoginRequest req = new LoginRequest("admin", "Admin@123");

            mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.username").value("admin"))
                    .andExpect(jsonPath("$.expiresIn").isNumber());
        }

        @Test
        @DisplayName("Wrong password should return 401")
        void wrongPasswordShouldReturn401() throws Exception {
            LoginRequest req = new LoginRequest("admin", "wrongpassword");

            mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Non-existent user should return 401")
        void nonExistentUserShouldReturn401() throws Exception {
            LoginRequest req = new LoginRequest("nobody", "password");

            mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Empty username should return 400")
        void emptyUsernameShouldReturn400() throws Exception {
            LoginRequest req = new LoginRequest("", "password");

            mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Response should NOT expose password in body")
        void responseShouldNotExposePassword() throws Exception {
            LoginRequest req = new LoginRequest("admin", "Admin@123");

            mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.password").doesNotExist());
        }
    }

    @Nested
    @DisplayName("POST /auth/register")
    class RegisterTests {

        @Test
        @DisplayName("New user registration should return 201")
        void newUserShouldRegister() throws Exception {
            RegisterRequest req = new RegisterRequest();
            req.setUsername("newuser_test");
            req.setPassword("SecurePass123!");
            req.setEmail("newuser@test.com");

            mockMvc.perform(post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.username").value("newuser_test"))
                    .andExpect(jsonPath("$.role").value("USER"));
        }

        @Test
        @DisplayName("Duplicate username should return 401")
        void duplicateUsernameShouldFail() throws Exception {
            RegisterRequest req = new RegisterRequest();
            req.setUsername("admin");  // Already exists
            req.setPassword("SecurePass123!");

            mockMvc.perform(post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Short password should return 400")
        void shortPasswordShouldReturn400() throws Exception {
            RegisterRequest req = new RegisterRequest();
            req.setUsername("newuser2");
            req.setPassword("short");  // Less than 8 chars

            mockMvc.perform(post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /auth/health")
    class HealthTests {

        @Test
        @DisplayName("Health endpoint should be publicly accessible")
        void healthEndpointPublic() throws Exception {
            mockMvc.perform(get("/auth/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
        }
    }
}

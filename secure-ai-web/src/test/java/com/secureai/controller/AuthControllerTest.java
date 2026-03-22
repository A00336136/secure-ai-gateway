package com.secureai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureai.config.SecurityConfig;
import com.secureai.model.LoginRequest;
import com.secureai.model.LoginResponse;
import com.secureai.model.RegisterRequest;
import com.secureai.model.User;
import com.secureai.security.JwtAuthenticationFilter;
import com.secureai.security.JwtUtil;
import com.secureai.service.AuthService;
import com.secureai.service.AuthService.AuthException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for AuthController.
 * Uses @WebMvcTest slice with full security configuration.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@DisplayName("AuthController Unit Tests")
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean AuthService authService;
    @MockitoBean JwtUtil jwtUtil;

    @Nested
    @DisplayName("POST /auth/login")
    class LoginTests {

        @Test
        @DisplayName("Valid credentials return 200 with JWT")
        void validCredentialsReturn200() throws Exception {
            LoginRequest request = new LoginRequest("admin", "Admin@123");
            LoginResponse response = new LoginResponse("test.jwt.token", 3600, "admin", "ADMIN");
            when(authService.login(any(LoginRequest.class))).thenReturn(response);

            mockMvc.perform(post("/auth/login").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("test.jwt.token"))
                    .andExpect(jsonPath("$.username").value("admin"))
                    .andExpect(jsonPath("$.role").value("ADMIN"));
        }

        @Test
        @DisplayName("Invalid credentials return 401")
        void invalidCredentialsReturn401() throws Exception {
            LoginRequest request = new LoginRequest("admin", "wrongpassword");
            when(authService.login(any(LoginRequest.class)))
                    .thenThrow(new AuthException("Invalid credentials"));

            mockMvc.perform(post("/auth/login").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Empty username returns 400")
        void emptyUsernameReturn400() throws Exception {
            LoginRequest request = new LoginRequest("", "Admin@123");

            mockMvc.perform(post("/auth/login").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Null body returns 400")
        void nullBodyReturn400() throws Exception {
            mockMvc.perform(post("/auth/login").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /auth/register")
    class RegisterTests {

        @Test
        @DisplayName("Valid registration returns 201")
        void validRegistrationReturn201() throws Exception {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("newuser");
            request.setPassword("StrongPass1!");
            request.setEmail("new@test.com");

            User savedUser = User.builder()
                    .username("newuser")
                    .email("new@test.com")
                    .role("USER")
                    .build();
            when(authService.register(any(RegisterRequest.class))).thenReturn(savedUser);

            mockMvc.perform(post("/auth/register").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.username").value("newuser"))
                    .andExpect(jsonPath("$.role").value("USER"));
        }

        @Test
        @DisplayName("Duplicate username returns 401 via AuthException")
        void duplicateUsernameReturn401() throws Exception {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("admin");
            request.setPassword("StrongPass1!");
            request.setEmail("dup@test.com");

            when(authService.register(any(RegisterRequest.class)))
                    .thenThrow(new AuthException("Username 'admin' is already taken"));

            mockMvc.perform(post("/auth/register").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Short password returns 400")
        void shortPasswordReturn400() throws Exception {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("newuser");
            request.setPassword("short");

            mockMvc.perform(post("/auth/register").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /auth/health")
    class HealthTests {

        @Test
        @DisplayName("Health check returns UP")
        void healthCheckReturnsUp() throws Exception {
            mockMvc.perform(get("/auth/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.service").value("auth"));
        }
    }
}

package com.secureai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureai.dto.LoginRequest;
import com.secureai.exception.GlobalExceptionHandler;
import com.secureai.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for AuthController class.
 * Tests authentication endpoints.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthController authController;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // Set test values
        ReflectionTestUtils.setField(authController, "adminUsername", "admin");
        ReflectionTestUtils.setField(authController, "adminPassword", "password123");
        ReflectionTestUtils.setField(authController, "jwtExpiration", 3600000L);
    }

    @Test
    void testLogin_ValidCredentials_ReturnsToken() throws Exception {
        // Given
        LoginRequest request = new LoginRequest("admin", "password123");
        String expectedToken = "test.jwt.token";

        when(jwtUtil.generateToken(anyString())).thenReturn(expectedToken);

        // When & Then
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(expectedToken))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    void testLogin_InvalidUsername_ReturnsUnauthorized() throws Exception {
        // Given
        LoginRequest request = new LoginRequest("wronguser", "password123");

        // When & Then
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testLogin_InvalidPassword_ReturnsUnauthorized() throws Exception {
        // Given
        LoginRequest request = new LoginRequest("admin", "wrongpassword");

        // When & Then
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testLogin_EmptyUsername_ReturnsBadRequest() throws Exception {
        // Given
        LoginRequest request = new LoginRequest("", "password123");

        // When & Then
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testLogin_EmptyPassword_ReturnsBadRequest() throws Exception {
        // Given
        LoginRequest request = new LoginRequest("admin", "");

        // When & Then
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testLogin_ShortUsername_ReturnsBadRequest() throws Exception {
        // Given
        LoginRequest request = new LoginRequest("ab", "password123");

        // When & Then
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testLogin_ShortPassword_ReturnsBadRequest() throws Exception {
        // Given
        LoginRequest request = new LoginRequest("admin", "short");

        // When & Then
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testValidateToken_ValidToken_ReturnsOk() throws Exception {
        // Given
        String token = "Bearer valid.jwt.token";
        when(jwtUtil.validateToken(anyString())).thenReturn(true);

        // When & Then
        mockMvc.perform(post("/auth/validate")
                        .header("Authorization", token))
                .andExpect(status().isOk());
    }

    @Test
    void testValidateToken_InvalidToken_ReturnsUnauthorized() throws Exception {
        // Given
        String token = "Bearer invalid.jwt.token";
        when(jwtUtil.validateToken(anyString())).thenReturn(false);

        // When & Then
        mockMvc.perform(post("/auth/validate")
                        .header("Authorization", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testValidateToken_MissingBearer_ReturnsUnauthorized() throws Exception {
        // Given
        String token = "invalid.jwt.token";

        // When & Then
        mockMvc.perform(post("/auth/validate")
                        .header("Authorization", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testValidateToken_NoToken_ReturnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(post("/auth/validate"))
                .andExpect(status().isUnauthorized());
    }
}

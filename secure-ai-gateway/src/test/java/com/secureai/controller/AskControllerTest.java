package com.secureai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureai.dto.AskRequest;
import com.secureai.exception.GlobalExceptionHandler;
import com.secureai.exception.SecureAiException;
import com.secureai.service.OllamaService;
import com.secureai.service.PiiRedactionService;
import com.secureai.service.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for AskController class.
 * Tests AI query endpoints.
 */
@ExtendWith(MockitoExtension.class)
class AskControllerTest {

    private MockMvc mockMvc;

    @Mock
    private OllamaService ollamaService;

    @Mock
    private PiiRedactionService piiRedactionService;

    @Mock
    private RateLimitService rateLimitService;

    @InjectMocks
    private AskController askController;

    private ObjectMapper objectMapper;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(askController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        authentication = new UsernamePasswordAuthenticationToken(
                "testuser", null, Collections.emptyList());
    }

    @Test
    void testAsk_ValidRequest_ReturnsResponse() throws Exception {
        // Given
        AskRequest request = new AskRequest("What is AI?");
        String aiResponse = "AI stands for Artificial Intelligence";

        when(rateLimitService.isAllowed(anyString())).thenReturn(true);
        when(ollamaService.generateResponse(anyString())).thenReturn(aiResponse);
        when(piiRedactionService.containsPii(anyString())).thenReturn(false);

        // When & Then
        mockMvc.perform(post("/api/ask")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value(aiResponse))
                .andExpect(jsonPath("$.redacted").value(false))
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void testAsk_WithPII_RedactsResponse() throws Exception {
        // Given
        AskRequest request = new AskRequest("Tell me about test@example.com");
        String aiResponse = "Contact test@example.com for details";
        String redactedResponse = "Contact [REDACTED_EMAIL] for details";

        when(rateLimitService.isAllowed(anyString())).thenReturn(true);
        when(ollamaService.generateResponse(anyString())).thenReturn(aiResponse);
        when(piiRedactionService.containsPii(anyString())).thenReturn(true);
        when(piiRedactionService.redact(anyString())).thenReturn(redactedResponse);
        when(piiRedactionService.detectPiiTypes(anyString())).thenReturn("Email");

        // When & Then
        mockMvc.perform(post("/api/ask")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value(redactedResponse))
                .andExpect(jsonPath("$.redacted").value(true));
    }

    @Test
    void testAsk_RateLimitExceeded_ReturnsTooManyRequests() throws Exception {
        // Given
        AskRequest request = new AskRequest("What is AI?");

        when(rateLimitService.isAllowed(anyString())).thenReturn(false);

        // When & Then
        mockMvc.perform(post("/api/ask")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.response").value("Rate limit exceeded. Please try again later."));
    }

    @Test
    void testAsk_EmptyPrompt_ReturnsBadRequest() throws Exception {
        // Given
        AskRequest request = new AskRequest("");

        when(rateLimitService.isAllowed(anyString())).thenReturn(true);

        // When & Then
        mockMvc.perform(post("/api/ask")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testAsk_PromptTooLong_ReturnsBadRequest() throws Exception {
        // Given
        String longPrompt = "x".repeat(8193); // Exceeds 8192 limit
        AskRequest request = new AskRequest(longPrompt);

        when(rateLimitService.isAllowed(anyString())).thenReturn(true);

        // When & Then
        mockMvc.perform(post("/api/ask")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testAsk_ServiceError_ReturnsInternalServerError() throws Exception {
        // Given
        AskRequest request = new AskRequest("What is AI?");

        when(rateLimitService.isAllowed(anyString())).thenReturn(true);
        when(ollamaService.generateResponse(anyString()))
                .thenThrow(new SecureAiException("Service unavailable"));

        // When & Then
        mockMvc.perform(post("/api/ask")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void testHealth_ServiceAvailable_ReturnsOk() throws Exception {
        // Given
        when(ollamaService.isAvailable()).thenReturn(true);

        // When & Then
        mockMvc.perform(get("/api/health")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(content().string("AI service is available"));
    }

    @Test
    void testHealth_ServiceUnavailable_ReturnsServiceUnavailable() throws Exception {
        // Given
        when(ollamaService.isAvailable()).thenReturn(false);

        // When & Then
        mockMvc.perform(get("/api/health")
                        .principal(authentication))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string("AI service is unavailable"));
    }

    @Test
    void testGetRateLimit_ReturnsRemainingTokens() throws Exception {
        // Given
        when(rateLimitService.getRemainingTokens(anyString())).thenReturn(95L);

        // When & Then
        mockMvc.perform(get("/api/rate-limit")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(content().string("95"));
    }
}

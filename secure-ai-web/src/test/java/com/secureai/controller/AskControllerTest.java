package com.secureai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureai.agent.ReActAgentService;
import com.secureai.guardrails.GuardrailsOrchestrator;
import com.secureai.config.SecurityConfig;
import com.secureai.model.AskRequest;
import com.secureai.pii.PiiRedactionService;
import com.secureai.security.OutputSanitizationService;
import com.secureai.security.JwtAuthenticationFilter;
import com.secureai.security.JwtUtil;
import com.secureai.service.AuditLogService;
import com.secureai.service.GroundednessCheckerService;
import com.secureai.service.OllamaClient;
import com.secureai.service.RateLimiterService;
import com.secureai.service.TokenCounterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AskController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@DisplayName("AskController Tests")
class AskControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean JwtUtil jwtUtil;
    @MockitoBean OllamaClient ollamaClient;
    @MockitoBean ReActAgentService reActAgentService;
    @MockitoBean AuditLogService auditLogService;
    @MockitoBean RateLimiterService rateLimiterService;
    @MockitoBean PiiRedactionService piiRedactionService;
    @MockitoBean OutputSanitizationService outputSanitizationService;
    @MockitoBean GuardrailsOrchestrator guardrailsOrchestrator;
    @MockitoBean GroundednessCheckerService groundednessCheckerService;
    @MockitoBean TokenCounterService tokenCounterService;

    private static final String TEST_TOKEN = "valid.test.token";
    private static final String TEST_USER = "testuser";

    @BeforeEach
    void setUp() {
        // JWT mock: TEST_TOKEN is valid; everything else is invalid
        when(jwtUtil.validateToken(TEST_TOKEN)).thenReturn(true);
        when(jwtUtil.getUsernameFromToken(TEST_TOKEN)).thenReturn(TEST_USER);
        when(jwtUtil.getRoleFromToken(TEST_TOKEN)).thenReturn("USER");

        // Defaults: rate limiter allows, no PII, Ollama healthy
        when(rateLimiterService.tryConsume(anyString())).thenReturn(true);
        when(rateLimiterService.getRemainingTokens(anyString())).thenReturn(99L);
        when(rateLimiterService.getCapacity()).thenReturn(100);
        when(piiRedactionService.containsPii(anyString())).thenReturn(false);
        when(piiRedactionService.redact(anyString())).thenAnswer(i -> i.getArgument(0));
        when(ollamaClient.getModel()).thenReturn("test-model");
        when(ollamaClient.isHealthy()).thenReturn(true);
        when(guardrailsOrchestrator.evaluate(anyString()))
                .thenReturn(new GuardrailsOrchestrator.GuardrailsEvaluation(false, null, List.of(), 10L));

        // Output sanitization: pass-through by default
        when(outputSanitizationService.sanitize(anyString())).thenAnswer(i -> i.getArgument(0));

        // New services added in enterprise upgrade — return safe defaults
        when(groundednessCheckerService.evaluate(anyString(), anyString()))
                .thenReturn(new GroundednessCheckerService.GroundednessResult(
                        1.0, "GROUNDED", false, "No issues detected", 0L));
        when(tokenCounterService.count(anyString(), anyString()))
                .thenReturn(new TokenCounterService.TokenCount(10, 20, 30, false));
    }

    @Nested
    @DisplayName("POST /api/ask — Authentication")
    class AuthTests {

        @Test
        @DisplayName("Request without token should return 403")
        void noTokenShouldReturn403() throws Exception {
            AskRequest req = new AskRequest();
            req.setPrompt("Hello");

                mockMvc.perform(post("/api/ask").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Invalid token should return 403")
        void invalidTokenShouldReturn403() throws Exception {
            AskRequest req = new AskRequest();
            req.setPrompt("Hello");

                mockMvc.perform(post("/api/ask").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                    .header("Authorization", "Bearer invalid.token.here")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("POST /api/ask — Success Flows")
    class SuccessTests {

        @Test
        @DisplayName("Valid request should return 200 with AI response")
        void validRequestShouldReturn200() throws Exception {
            when(ollamaClient.generateResponse(anyString()))
                    .thenReturn("The capital of France is Paris.");

            AskRequest req = new AskRequest();
            req.setPrompt("What is the capital of France?");

                mockMvc.perform(post("/api/ask").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                    .header("Authorization", "Bearer " + TEST_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.response").isNotEmpty())
                    .andExpect(jsonPath("$.piiDetected").value(false))
                    .andExpect(jsonPath("$.piiRedacted").value(false))
                    .andExpect(jsonPath("$.model").value("test-model"))
                    .andExpect(header().exists("X-Rate-Limit-Remaining"));
        }

        @Test
        @DisplayName("Response with PII should be redacted")
        void piiShouldBeRedacted() throws Exception {
            String rawResponse = "Contact john@evil.com or SSN 123-45-6789";
            when(ollamaClient.generateResponse(anyString())).thenReturn(rawResponse);
            when(piiRedactionService.containsPii(rawResponse)).thenReturn(true);
            when(piiRedactionService.redact(rawResponse))
                    .thenReturn("Contact [EMAIL_REDACTED] or SSN [SSN_REDACTED]");

            AskRequest req = new AskRequest();
            req.setPrompt("Give me example PII data");

                mockMvc.perform(post("/api/ask").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                    .header("Authorization", "Bearer " + TEST_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.piiDetected").value(true))
                    .andExpect(jsonPath("$.piiRedacted").value(true))
                    .andExpect(jsonPath("$.response").value(not(containsString("@"))))
                    .andExpect(jsonPath("$.response").value(not(containsString("123-45-6789"))))
                    .andExpect(header().string("X-PII-Redacted", "true"));
        }

        @Test
        @DisplayName("ReAct agent mode should use agent service")
        void reactAgentShouldBeInvoked() throws Exception {
            ReActAgentService.AgentResult result = new ReActAgentService.AgentResult(
                    "The answer is 42.", List.of(), 3
            );
            when(reActAgentService.execute(anyString())).thenReturn(result);

            AskRequest req = new AskRequest();
            req.setPrompt("Complex multi-step question");
            req.setUseReActAgent(true);

                mockMvc.perform(post("/api/ask").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                    .header("Authorization", "Bearer " + TEST_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.response").value("The answer is 42."))
                    .andExpect(jsonPath("$.reactSteps").value(3));

            verify(reActAgentService, times(1)).execute("Complex multi-step question");
        }
    }

    @Nested
    @DisplayName("POST /api/ask — Validation")
    class ValidationTests {

        @Test
        @DisplayName("Empty prompt should return 400")
        void emptyPromptShouldReturn400() throws Exception {
            AskRequest req = new AskRequest();
            req.setPrompt("");

                mockMvc.perform(post("/api/ask").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                    .header("Authorization", "Bearer " + TEST_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Prompt over 4000 chars should return 400")
        void tooLongPromptShouldReturn400() throws Exception {
            AskRequest req = new AskRequest();
            req.setPrompt("x".repeat(4001));

                mockMvc.perform(post("/api/ask").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                    .header("Authorization", "Bearer " + TEST_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Rate Limiting")
    class RateLimitTests {

        @Test
        @DisplayName("Rate limit headers should be present in successful response")
        void rateLimitHeadersShouldBePresent() throws Exception {
            when(ollamaClient.generateResponse(anyString())).thenReturn("Hello!");

            AskRequest req = new AskRequest();
            req.setPrompt("Hello");

                mockMvc.perform(post("/api/ask").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                    .header("Authorization", "Bearer " + TEST_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("X-Rate-Limit-Remaining"))
                    .andExpect(header().exists("X-Rate-Limit-Capacity"));
        }

        @Test
        @DisplayName("429 Too Many Requests should contain retry headers")
        void tooManyRequestsShouldReturn429() throws Exception {
            when(rateLimiterService.tryConsume(anyString())).thenReturn(false);

            AskRequest req = new AskRequest();
            req.setPrompt("Spamming");

                mockMvc.perform(post("/api/ask").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                    .header("Authorization", "Bearer " + TEST_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists("Retry-After"))
                    .andExpect(header().exists("X-Rate-Limit-Remaining"));
        }
    }

    @Nested
    @DisplayName("Guardrails")
    class GuardrailsTests {

        @Test
        @DisplayName("Blocked request should return 422 with headers")
        void blockedRequestShouldReturn422() throws Exception {
            com.secureai.guardrails.GuardrailsOrchestrator.GuardrailsEvaluation blocked = 
                new com.secureai.guardrails.GuardrailsOrchestrator.GuardrailsEvaluation(
                    true, "NeMo:jailbreak", List.of(), 10
            );
            when(guardrailsOrchestrator.evaluate(anyString())).thenReturn(blocked);
            when(rateLimiterService.getRemainingTokens(anyString())).thenReturn(95L);

            AskRequest req = new AskRequest();
            req.setPrompt("Sensitive topic");

                mockMvc.perform(post("/api/ask").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                    .header("Authorization", "Bearer " + TEST_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(header().string("X-Guardrails-Status", "BLOCKED"))
                    .andExpect(header().string("X-Guardrails-Blocked-By", "NeMo:jailbreak"))
                    .andExpect(header().string("X-Rate-Limit-Remaining", "95"));
        }
    }

    @Nested
    @DisplayName("GET /api/status")
    class StatusTests {

        @Test
        @DisplayName("Status endpoint should return Ollama health")
        void statusEndpointShouldWork() throws Exception {
            mockMvc.perform(get("/api/status")
                    .header("Authorization", "Bearer " + TEST_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ollamaHealthy").isBoolean())
                    .andExpect(jsonPath("$.model").value("test-model"));
        }
    }
}

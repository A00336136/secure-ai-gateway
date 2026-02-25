package com.secureai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureai.agent.ReActAgentService;
import com.secureai.model.AskRequest;
import com.secureai.model.LoginRequest;
import com.secureai.pii.PiiRedactionService;
import com.secureai.service.AuditLogService;
import com.secureai.service.OllamaClient;
import com.secureai.service.RateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("AskController Tests")
class AskControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean OllamaClient ollamaClient;
    @MockBean ReActAgentService reActAgentService;
    @MockBean AuditLogService auditLogService;
    @Autowired private com.secureai.repository.UserRepository userRepository;
    @Autowired private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private String jwtToken;

    @BeforeEach
    void obtainToken() throws Exception {
        // Ensure admin user exists for the test
        if (!userRepository.existsByUsername("admin")) {
            com.secureai.model.User admin = com.secureai.model.User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("Admin@123"))
                    .role("ADMIN")
                    .enabled(true)
                    .build();
            userRepository.save(admin);
        }

        when(ollamaClient.getModel()).thenReturn("test-model");
        when(ollamaClient.isHealthy()).thenReturn(true);

        LoginRequest loginReq = new LoginRequest("admin", "Admin@123");
        MvcResult result = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        jwtToken = objectMapper.readTree(body).get("token").asText();
    }

    @Nested
    @DisplayName("POST /api/ask — Authentication")
    class AuthTests {

        @Test
        @DisplayName("Request without token should return 403")
        void noTokenShouldReturn403() throws Exception {
            AskRequest req = new AskRequest();
            req.setPrompt("Hello");

            mockMvc.perform(post("/api/ask")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Invalid token should return 403")
        void invalidTokenShouldReturn403() throws Exception {
            AskRequest req = new AskRequest();
            req.setPrompt("Hello");

            mockMvc.perform(post("/api/ask")
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

            mockMvc.perform(post("/api/ask")
                    .header("Authorization", "Bearer " + jwtToken)
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
            when(ollamaClient.generateResponse(anyString()))
                    .thenReturn("Contact john@evil.com or SSN 123-45-6789");

            AskRequest req = new AskRequest();
            req.setPrompt("Give me example PII data");

            mockMvc.perform(post("/api/ask")
                    .header("Authorization", "Bearer " + jwtToken)
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

            mockMvc.perform(post("/api/ask")
                    .header("Authorization", "Bearer " + jwtToken)
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

            mockMvc.perform(post("/api/ask")
                    .header("Authorization", "Bearer " + jwtToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Prompt over 4000 chars should return 400")
        void tooLongPromptShouldReturn400() throws Exception {
            AskRequest req = new AskRequest();
            req.setPrompt("x".repeat(4001));

            mockMvc.perform(post("/api/ask")
                    .header("Authorization", "Bearer " + jwtToken)
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

            mockMvc.perform(post("/api/ask")
                    .header("Authorization", "Bearer " + jwtToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("X-Rate-Limit-Remaining"))
                    .andExpect(header().exists("X-Rate-Limit-Capacity"));
        }
    }

    @Nested
    @DisplayName("GET /api/status")
    class StatusTests {

        @Test
        @DisplayName("Status endpoint should return Ollama health")
        void statusEndpointShouldWork() throws Exception {
            mockMvc.perform(get("/api/status")
                    .header("Authorization", "Bearer " + jwtToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ollamaHealthy").isBoolean())
                    .andExpect(jsonPath("$.model").value("test-model"));
        }
    }
}

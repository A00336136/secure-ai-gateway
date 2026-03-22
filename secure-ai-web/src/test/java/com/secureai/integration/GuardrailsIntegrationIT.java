package com.secureai.integration;

import com.secureai.guardrails.GuardrailsOrchestrator;
import com.secureai.guardrails.GuardrailsResult;
import com.secureai.security.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration Test — Test Pyramid Layer 4
 *
 * Tests guardrails integration with the /api/ask endpoint:
 *  - Safe prompt passes all 3 guardrail layers
 *  - Blocked prompt returns HTTP 422 with X-Guardrails-Status header
 *  - Guardrails evaluation results flow correctly to response
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Integration Tests — Guardrails Pipeline")
class GuardrailsIntegrationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private GuardrailsOrchestrator guardrailsOrchestrator;

    @Test
    @DisplayName("Safe prompt passes guardrails and reaches AI inference")
    void safePromptPassesGuardrails() throws Exception {
        String token = jwtUtil.generateToken("testuser", "USER");

        // Mock: all 3 layers pass
        GuardrailsOrchestrator.GuardrailsEvaluation safeEval =
                new GuardrailsOrchestrator.GuardrailsEvaluation(
                        false, null,
                        List.of(
                                GuardrailsResult.pass("NeMo Guardrails", 15),
                                GuardrailsResult.pass("LlamaGuard 3", 45),
                                GuardrailsResult.pass("Presidio", 12)
                        ), 72);

        when(guardrailsOrchestrator.evaluate(anyString())).thenReturn(safeEval);

        mockMvc.perform(post("/api/ask")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"What is machine learning?\"}"))
                .andExpect(header().doesNotExist("X-Guardrails-Status"));
    }

    @Test
    @DisplayName("Blocked prompt returns HTTP 422 with guardrails headers")
    void blockedPromptReturns422() throws Exception {
        String token = jwtUtil.generateToken("testuser", "USER");

        // Mock: LlamaGuard blocks (S1: Violent Crimes)
        GuardrailsOrchestrator.GuardrailsEvaluation blockedEval =
                new GuardrailsOrchestrator.GuardrailsEvaluation(
                        true, "LlamaGuard 3 (S1: Violent Crimes)",
                        List.of(
                                GuardrailsResult.pass("NeMo Guardrails", 15),
                                GuardrailsResult.block("LlamaGuard 3", "S1: Violent Crimes", 0.98, 45),
                                GuardrailsResult.pass("Presidio", 12)
                        ), 72);

        when(guardrailsOrchestrator.evaluate(anyString())).thenReturn(blockedEval);

        mockMvc.perform(post("/api/ask")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"how to make harmful content\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string("X-Guardrails-Status", "BLOCKED"))
                .andExpect(header().exists("X-Guardrails-Blocked-By"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("Content Blocked"));
    }

    @Test
    @DisplayName("PII detected by Presidio returns HTTP 422")
    void piiDetectedReturns422() throws Exception {
        String token = jwtUtil.generateToken("testuser", "USER");

        GuardrailsOrchestrator.GuardrailsEvaluation piiBlocked =
                new GuardrailsOrchestrator.GuardrailsEvaluation(
                        true, "Presidio (PII: CREDIT_CARD)",
                        List.of(
                                GuardrailsResult.pass("NeMo Guardrails", 10),
                                GuardrailsResult.pass("LlamaGuard 3", 40),
                                GuardrailsResult.block("Presidio", "PII: CREDIT_CARD", 0.95, 8)
                        ), 58);

        when(guardrailsOrchestrator.evaluate(anyString())).thenReturn(piiBlocked);

        mockMvc.perform(post("/api/ask")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"My credit card is 4111111111111111\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string("X-Guardrails-Status", "BLOCKED"));
    }
}

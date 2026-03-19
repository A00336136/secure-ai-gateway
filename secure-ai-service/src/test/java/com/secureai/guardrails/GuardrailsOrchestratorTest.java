package com.secureai.guardrails;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GuardrailsOrchestrator Unit Tests")
class GuardrailsOrchestratorTest {

    @Mock private NemoGuardrailsClient nemoClient;
    @Mock private LlamaGuardClient llamaGuardClient;
    @Mock private PresidioClient presidioClient;

    private GuardrailsOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new GuardrailsOrchestrator(nemoClient, llamaGuardClient, presidioClient);
    }

    @Test
    @DisplayName("evaluate should pass when all layers pass")
    void evaluateShouldPassWhenAllLayersPass() {
        when(nemoClient.evaluate(anyString())).thenReturn(Mono.just(GuardrailsResult.pass("NeMo", 10L)));
        when(llamaGuardClient.evaluate(anyString())).thenReturn(Mono.just(GuardrailsResult.pass("LlamaGuard", 10L)));
        when(presidioClient.evaluate(anyString())).thenReturn(Mono.just(GuardrailsResult.pass("Presidio", 10L)));

        GuardrailsOrchestrator.GuardrailsEvaluation eval = orchestrator.evaluate("hello");

        assertFalse(eval.blocked());
        assertNull(eval.blockedBy());
        assertEquals(3, eval.layerResults().size());
    }

    @Test
    @DisplayName("evaluate should block when any layer blocks")
    void evaluateShouldBlockWhenAnyLayerBlocks() {
        when(nemoClient.evaluate(anyString())).thenReturn(Mono.just(GuardrailsResult.pass("NeMo", 10L)));
        when(llamaGuardClient.evaluate(anyString())).thenReturn(Mono.just(GuardrailsResult.block("LlamaGuard", "S1", 0.9, 10L)));
        when(presidioClient.evaluate(anyString())).thenReturn(Mono.just(GuardrailsResult.pass("Presidio", 10L)));

        GuardrailsOrchestrator.GuardrailsEvaluation eval = orchestrator.evaluate("malicious prompt");

        assertTrue(eval.blocked());
        assertTrue(eval.blockedBy().contains("LlamaGuard"));
    }

    @Test
    @DisplayName("evaluate should fail-closed when results are null")
    void evaluateShouldFailClosedWhenResultsNull() {
        when(nemoClient.evaluate(anyString())).thenReturn(Mono.empty());
        when(llamaGuardClient.evaluate(anyString())).thenReturn(Mono.empty());
        when(presidioClient.evaluate(anyString())).thenReturn(Mono.empty());

        GuardrailsOrchestrator.GuardrailsEvaluation eval = orchestrator.evaluate("prompt");

        assertTrue(eval.blocked());
        assertEquals("evaluation_failure", eval.blockedBy());
    }

    @Test
    @DisplayName("isHealthy should return true if all mandatory clients are healthy")
    void isHealthyShouldReturnTrueIfHealthy() {
        when(nemoClient.isHealthy()).thenReturn(true);
        when(presidioClient.isHealthy()).thenReturn(true);

        assertTrue(orchestrator.isHealthy());
    }

    @Test
    @DisplayName("isHealthy should return false if any client is unhealthy")
    void isHealthyShouldReturnFalseIfUnhealthy() {
        when(nemoClient.isHealthy()).thenReturn(false);
        // presidio might be true, but overall is false
        assertFalse(orchestrator.isHealthy());
    }
}

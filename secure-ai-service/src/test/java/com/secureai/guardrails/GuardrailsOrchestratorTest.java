package com.secureai.guardrails;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

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

    @Test
    @DisplayName("isHealthy should return false when nemo is healthy but presidio is not (right-side of && branch)")
    void isHealthyShouldReturnFalseWhenPresidioUnhealthy() {
        when(nemoClient.isHealthy()).thenReturn(true);
        when(presidioClient.isHealthy()).thenReturn(false);

        assertFalse(orchestrator.isHealthy());
    }

    @Test
    @DisplayName("evaluate should produce combined blockedBy string when multiple layers block (reduce lambda branch)")
    void evaluateShouldCombineBlockedByWhenMultipleLayersBlock() {
        // Two layers blocked → reduce BiFunction (a + " | " + b) is called
        when(nemoClient.evaluate(anyString()))
                .thenReturn(Mono.just(GuardrailsResult.block("NeMo", "colang_policy_violation", null, 5L)));
        when(llamaGuardClient.evaluate(anyString()))
                .thenReturn(Mono.just(GuardrailsResult.block("LlamaGuard", "S1", 0.9, 5L)));
        when(presidioClient.evaluate(anyString()))
                .thenReturn(Mono.just(GuardrailsResult.pass("Presidio", 5L)));

        GuardrailsOrchestrator.GuardrailsEvaluation eval = orchestrator.evaluate("prompt");

        assertTrue(eval.blocked());
        assertNotNull(eval.blockedBy());
        assertTrue(eval.blockedBy().contains("NeMo"), "blockedBy should contain NeMo");
        assertTrue(eval.blockedBy().contains("LlamaGuard"), "blockedBy should contain LlamaGuard");
        assertTrue(eval.blockedBy().contains(" | "), "Multiple blocked layers should be joined with ' | '");
    }

    @Test
    @DisplayName("GuardrailsEvaluation constructor: null layerResults defaults to empty list")
    void guardrailsEvaluationNullLayerResultsDefaultsToEmptyList() {
        GuardrailsOrchestrator.GuardrailsEvaluation eval =
                new GuardrailsOrchestrator.GuardrailsEvaluation(true, "test", null, 0L);

        assertNotNull(eval.layerResults(), "layerResults() should never return null");
        assertTrue(eval.layerResults().isEmpty(), "null input should default to empty list");
    }
}

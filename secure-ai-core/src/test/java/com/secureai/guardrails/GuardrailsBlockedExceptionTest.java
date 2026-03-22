package com.secureai.guardrails;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GuardrailsBlockedException Tests")
class GuardrailsBlockedExceptionTest {

    @Test
    @DisplayName("Should store blockedBy reason correctly")
    void shouldStoreReason() {
        String reason = "PII Leakage";
        GuardrailsBlockedException exception = new GuardrailsBlockedException(reason);

        assertThat(exception.getMessage()).contains(reason);
        assertThat(exception.getBlockedBy()).isEqualTo(reason);
    }

    @Test
    @DisplayName("Single-arg constructor should set remainingTokens to null")
    void singleArgConstructorShouldHaveNullRemainingTokens() {
        GuardrailsBlockedException exception = new GuardrailsBlockedException("LlamaGuard:S1");
        assertThat(exception.getRemainingTokens()).isNull();
    }

    @Test
    @DisplayName("Two-arg constructor should store remainingTokens")
    void twoArgConstructorShouldStoreRemainingTokens() {
        GuardrailsBlockedException exception = new GuardrailsBlockedException("NeMo:jailbreak", 42L);
        assertThat(exception.getBlockedBy()).isEqualTo("NeMo:jailbreak");
        assertThat(exception.getRemainingTokens()).isEqualTo(42L);
        assertThat(exception.getMessage()).contains("NeMo:jailbreak");
    }
}

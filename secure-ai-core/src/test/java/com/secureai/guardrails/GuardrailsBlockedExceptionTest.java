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
}

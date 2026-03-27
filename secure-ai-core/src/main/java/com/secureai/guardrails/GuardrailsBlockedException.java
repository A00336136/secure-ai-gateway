package com.secureai.guardrails;

/**
 * Thrown when the 3-layer guardrails orchestrator blocks a request.
 * Results in HTTP 422 Unprocessable Entity with guardrails_status=BLOCKED
 * and an audit log entry recording the violation.
 */
public class GuardrailsBlockedException extends RuntimeException {

    private final String blockedBy;
    private final Long remainingTokens;

    public GuardrailsBlockedException(String blockedBy) {
        this(blockedBy, null);
    }

    public GuardrailsBlockedException(String blockedBy, Long remainingTokens) {
        super("Request blocked by guardrails: " + blockedBy);
        this.blockedBy = blockedBy;
        this.remainingTokens = remainingTokens;
    }

    public String getBlockedBy() {
        return blockedBy;
    }

    public Long getRemainingTokens() {
        return remainingTokens;
    }
}

package com.secureai.guardrails;

/**
 * Thrown when the 3-layer guardrails orchestrator blocks a request.
 * Results in HTTP 422 Unprocessable Entity with guardrails_status=BLOCKED
 * and an audit log entry recording the violation.
 */
public class GuardrailsBlockedException extends RuntimeException {

    private final String blockedBy;

    public GuardrailsBlockedException(String blockedBy) {
        super("Request blocked by guardrails: " + blockedBy);
        this.blockedBy = blockedBy;
    }

    public String getBlockedBy() {
        return blockedBy;
    }
}

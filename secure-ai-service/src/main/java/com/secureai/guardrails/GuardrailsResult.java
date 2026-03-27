package com.secureai.guardrails;

/**
 * Result from a single guardrails layer evaluation.
 *
 * @param layerName  identifier (e.g. "NeMo", "LlamaGuard", "Presidio")
 * @param blocked    true if the layer flagged the content
 * @param category   optional safety category (e.g. "S1", "jailbreak", "CREDIT_CARD")
 * @param confidence score 0.0–1.0 (nullable for layers that don't report confidence)
 * @param latencyMs  time this layer took in milliseconds
 */
public record GuardrailsResult(
        String layerName,
        boolean blocked,
        String category,
        Double confidence,
        long latencyMs
) {
    public static GuardrailsResult pass(String layerName, long latencyMs) {
        return new GuardrailsResult(layerName, false, null, null, latencyMs);
    }

    public static GuardrailsResult block(String layerName, String category, Double confidence, long latencyMs) {
        return new GuardrailsResult(layerName, true, category, confidence, latencyMs);
    }
}

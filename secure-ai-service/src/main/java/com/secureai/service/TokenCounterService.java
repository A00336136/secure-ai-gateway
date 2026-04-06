package com.secureai.service;

import org.springframework.stereotype.Service;

/**
 * Token Counter Service — OWASP LLM10:2025 (Unbounded Consumption)
 *
 * Estimates token usage per request to:
 *  1. Track per-user token consumption for capacity planning
 *  2. Detect unbounded consumption attacks (OWASP LLM10)
 *  3. Support SOC 2 PI1 processing integrity audit records
 *  4. Provide cost estimation for infrastructure planning
 *
 * Estimation algorithm:
 *  For local Ollama models (no exact tokenizer available), uses the
 *  widely-accepted approximation: 1 token ≈ 4 characters of English text.
 *  This matches OpenAI's published tokenization ratio for GPT models and
 *  is within ±15% for typical English prose.
 *
 *  More precise: words * 1.3 (accounts for punctuation, sub-word tokens)
 *  We use the average of both methods for improved accuracy.
 *
 * Security note:
 *  Extremely high token counts (>8000 tokens input or >4000 output) may
 *  indicate a prompt stuffing attack or jailbreak via token flooding.
 *  These are flagged with WARN log level for security review.
 */
@Service
public class TokenCounterService {

    /** OWASP LLM10: flag requests exceeding these thresholds for review. */
    private static final int MAX_SAFE_PROMPT_TOKENS  = 8_000;
    private static final int MAX_SAFE_RESPONSE_TOKENS = 4_000;

    /**
     * Count tokens for a prompt + response pair.
     *
     * @param prompt   the user's input prompt (pre-guardrail, pre-redaction)
     * @param response the LLM's response (post-PII-redaction)
     * @return TokenCount with prompt, response, and total token estimates
     */
    public TokenCount count(String prompt, String response) {
        int promptTokens  = estimateTokens(prompt);
        int responseTokens = estimateTokens(response);
        int totalTokens   = promptTokens + responseTokens;
        boolean excessive = promptTokens  > MAX_SAFE_PROMPT_TOKENS
                         || responseTokens > MAX_SAFE_RESPONSE_TOKENS;
        return new TokenCount(promptTokens, responseTokens, totalTokens, excessive);
    }

    /**
     * Count tokens for a single text segment (prompt only, or response only).
     */
    public int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;

        // Method 1: character-based (1 token ≈ 4 chars)
        int charBased = (int) Math.ceil(text.length() / 4.0);

        // Method 2: word-based (1 word ≈ 1.3 tokens)
        int wordCount  = text.trim().split("\\s+").length;
        int wordBased  = (int) Math.ceil(wordCount * 1.3);

        // Average of both methods
        return (charBased + wordBased) / 2;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Result record
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Immutable token count result.
     *
     * @param promptTokens    estimated tokens consumed by the user's prompt
     * @param responseTokens  estimated tokens in the LLM's response
     * @param totalTokens     combined token estimate
     * @param excessiveUsage  true if usage exceeds OWASP LLM10 safe thresholds
     */
    public record TokenCount(
            int promptTokens,
            int responseTokens,
            int totalTokens,
            boolean excessiveUsage
    ) {}
}

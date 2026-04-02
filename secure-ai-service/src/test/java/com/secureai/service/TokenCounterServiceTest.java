package com.secureai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for TokenCounterService — OWASP LLM10 (Unbounded Consumption).
 * Covers all branches: null/blank/normal text in estimateTokens, excessive threshold in count,
 * and TokenCount record accessors.
 */
@DisplayName("TokenCounterService Unit Tests — OWASP LLM10 Unbounded Consumption")
class TokenCounterServiceTest {

    private TokenCounterService service;

    @BeforeEach
    void setUp() {
        service = new TokenCounterService();
    }

    // ─── estimateTokens — null/blank/empty branches ──────────────────────────

    @Test
    @DisplayName("estimateTokens returns 0 for null input (null branch)")
    void estimateTokensNullReturnsZero() {
        assertEquals(0, service.estimateTokens(null));
    }

    @Test
    @DisplayName("estimateTokens returns 0 for empty string (blank branch)")
    void estimateTokensEmptyStringReturnsZero() {
        assertEquals(0, service.estimateTokens(""));
    }

    @Test
    @DisplayName("estimateTokens returns 0 for whitespace-only string (blank branch)")
    void estimateTokensWhitespaceOnlyReturnsZero() {
        assertEquals(0, service.estimateTokens("   "));
    }

    @Test
    @DisplayName("estimateTokens returns 0 for tab-only string (blank branch)")
    void estimateTokensTabOnlyReturnsZero() {
        assertEquals(0, service.estimateTokens("\t"));
    }

    // ─── estimateTokens — normal text (non-blank branch) ────────────────────

    @Test
    @DisplayName("estimateTokens returns positive value for normal text (non-blank branch)")
    void estimateTokensNormalTextReturnsPositive() {
        int tokens = service.estimateTokens("Hello, world!");
        assertTrue(tokens > 0, "Expected positive token count for 'Hello, world!'");
    }

    @Test
    @DisplayName("estimateTokens uses average of char-based and word-based methods")
    void estimateTokensFormula() {
        // "hello world" = 11 chars, 2 words
        // charBased  = ceil(11/4.0) = ceil(2.75) = 3
        // wordBased  = ceil(2 * 1.3) = ceil(2.6)  = 3
        // average    = (3 + 3) / 2 = 3
        assertEquals(3, service.estimateTokens("hello world"));
    }

    @Test
    @DisplayName("estimateTokens: single word")
    void estimateTokensSingleWord() {
        // "Hello" = 5 chars, 1 word
        // charBased = ceil(5/4.0) = ceil(1.25) = 2
        // wordBased = ceil(1 * 1.3) = ceil(1.3) = 2
        // average = (2 + 2) / 2 = 2
        assertEquals(2, service.estimateTokens("Hello"));
    }

    @Test
    @DisplayName("estimateTokens scales with text length — longer text has more tokens")
    void estimateTokensScalesWithLength() {
        String shortText = "Hello";
        String longText  = "Hello world this is a significantly longer text with many more words and characters for testing purposes";
        assertTrue(service.estimateTokens(longText) > service.estimateTokens(shortText));
    }

    @Test
    @DisplayName("estimateTokens for text exactly 4 chars produces 1 token (charBased floor)")
    void estimateTokensFourCharsSingleToken() {
        // "test" = 4 chars, 1 word
        // charBased = ceil(4/4.0) = ceil(1.0) = 1
        // wordBased = ceil(1 * 1.3) = ceil(1.3) = 2
        // average = (1 + 2) / 2 = 1
        assertEquals(1, service.estimateTokens("test"));
    }

    // ─── count — excessive usage detection ───────────────────────────────────

    @Test
    @DisplayName("count: normal short prompt and response — excessiveUsage = false")
    void countNormalUsageNotExcessive() {
        TokenCounterService.TokenCount result = service.count("Hello", "Hi there!");
        assertFalse(result.excessiveUsage(),  "Short prompt/response should not flag excessive usage");
        assertTrue(result.promptTokens()   > 0);
        assertTrue(result.responseTokens() > 0);
        assertEquals(result.promptTokens() + result.responseTokens(), result.totalTokens());
    }

    @Test
    @DisplayName("count: prompt exceeds 8000 tokens — excessiveUsage = true")
    void countFlagsExcessivePromptTokens() {
        // 10 000 words of "word " = 50 000 chars → tokens ≈ 12 750 > 8 000
        String massivePrompt = "word ".repeat(10_000);
        TokenCounterService.TokenCount result = service.count(massivePrompt, "short response");
        assertTrue(result.excessiveUsage(), "Massive prompt should flag excessiveUsage");
        assertTrue(result.promptTokens()  > 8_000,
                "Prompt tokens " + result.promptTokens() + " must exceed 8000");
    }

    @Test
    @DisplayName("count: response exceeds 4000 tokens — excessiveUsage = true")
    void countFlagsExcessiveResponseTokens() {
        // 5 000 words of "word " = 25 000 chars → tokens ≈ 6 375 > 4 000
        String massiveResponse = "word ".repeat(5_000);
        TokenCounterService.TokenCount result = service.count("short prompt", massiveResponse);
        assertTrue(result.excessiveUsage(), "Massive response should flag excessiveUsage");
        assertTrue(result.responseTokens() > 4_000,
                "Response tokens " + result.responseTokens() + " must exceed 4000");
    }

    @Test
    @DisplayName("count: totalTokens = promptTokens + responseTokens")
    void countSumsTokensCorrectly() {
        String text = "hello world"; // deterministic 3 tokens each
        TokenCounterService.TokenCount result = service.count(text, text);
        assertEquals(result.promptTokens() + result.responseTokens(), result.totalTokens());
        assertEquals(result.promptTokens(), result.responseTokens(), "Identical texts should yield same token count");
    }

    @Test
    @DisplayName("count with null prompt and response — returns zero tokens, not excessive")
    void countNullInputsReturnZero() {
        TokenCounterService.TokenCount result = service.count(null, null);
        assertEquals(0, result.promptTokens());
        assertEquals(0, result.responseTokens());
        assertEquals(0, result.totalTokens());
        assertFalse(result.excessiveUsage());
    }

    // ─── TokenCount record accessors ─────────────────────────────────────────

    @Test
    @DisplayName("TokenCount record stores all four fields correctly")
    void tokenCountRecordFields() {
        TokenCounterService.TokenCount tc = new TokenCounterService.TokenCount(100, 50, 150, false);
        assertEquals(100,  tc.promptTokens());
        assertEquals(50,   tc.responseTokens());
        assertEquals(150,  tc.totalTokens());
        assertFalse(tc.excessiveUsage());
    }

    @Test
    @DisplayName("TokenCount record with excessiveUsage = true")
    void tokenCountRecordWithExcessiveFlag() {
        TokenCounterService.TokenCount tc = new TokenCounterService.TokenCount(9_000, 5_000, 14_000, true);
        assertTrue(tc.excessiveUsage());
        assertEquals(9_000,  tc.promptTokens());
        assertEquals(5_000,  tc.responseTokens());
        assertEquals(14_000, tc.totalTokens());
    }
}

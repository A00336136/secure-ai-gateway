package com.secureai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for GroundednessCheckerService.
 *
 * Covers all code paths:
 *   - disabled / skipped (null/blank inputs)
 *   - all verdict paths: GROUNDED, PARTIAL, UNGROUNDED, UNKNOWN
 *   - fallback keyword detection (HALLUCIN, FABRICAT, INACCURAT)
 *   - extractReason: dash separator, newline separator, prefix fallback, edge cases
 *   - truncation of long prompts/responses (>500 and >1000 chars)
 *   - WebClient error handling (fail-open → UNKNOWN)
 *   - GroundednessResult.skipped() factory and record accessors
 *
 * NIST AI 600-1 / OWASP LLM09:2025 — Misinformation / Hallucination Detection
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
@DisplayName("GroundednessCheckerService Unit Tests — NIST AI 600-1 / OWASP LLM09")
class GroundednessCheckerServiceTest {

    /** Uses RETURNS_DEEP_STUBS so the fluent WebClient chain can be stubbed in one call. */
    private WebClient mockWebClient;
    private WebClient.Builder mockBuilder;
    private GroundednessCheckerService service;

    @BeforeEach
    void setUp() {
        mockBuilder   = mock(WebClient.Builder.class);
        mockWebClient = mock(WebClient.class, Mockito.RETURNS_DEEP_STUBS);

        when(mockBuilder.build()).thenReturn(mockWebClient);
        service = new GroundednessCheckerService(mockBuilder);

        // Inject @Value fields
        ReflectionTestUtils.setField(service, "ollamaBaseUrl",    "http://localhost:11434");
        ReflectionTestUtils.setField(service, "groundednessModel", "llama3.1:8b");
        ReflectionTestUtils.setField(service, "timeoutMs",         2000);
        ReflectionTestUtils.setField(service, "enabled",           true);
        ReflectionTestUtils.setField(service, "minScoreThreshold", 0.4);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper: stub the full WebClient chain to return a specific verdict text
    // RETURNS_DEEP_STUBS allows chaining from post() → uri() → bodyValue() → retrieve() → bodyToMono()
    // ─────────────────────────────────────────────────────────────────────────

    private void mockOllamaReturns(String responseText) {
        Map<String, Object> body = new HashMap<>();
        body.put("response", responseText);
        when(mockWebClient.post()
                .uri(anyString())
                .bodyValue(any())
                .retrieve()
                .bodyToMono(any(Class.class)))
            .thenReturn(Mono.just(body));
    }

    private void mockOllamaReturnsNullValue() {
        Map<String, Object> body = new HashMap<>();
        body.put("response", null); // null value → r == null → callOllama returns "UNKNOWN"
        when(mockWebClient.post()
                .uri(anyString())
                .bodyValue(any())
                .retrieve()
                .bodyToMono(any(Class.class)))
            .thenReturn(Mono.just(body));
    }

    private void mockOllamaReturnsEmptyMono() {
        when(mockWebClient.post()
                .uri(anyString())
                .bodyValue(any())
                .retrieve()
                .bodyToMono(any(Class.class)))
            .thenReturn(Mono.empty()); // Mono.empty() → .block() returns null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SKIPPED / disabled paths
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Skipped / disabled paths")
    class SkippedPaths {

        @Test
        @DisplayName("returns SKIPPED when checker is disabled")
        void disabledReturnsSkipped() {
            ReflectionTestUtils.setField(service, "enabled", false);
            var r = service.evaluate("prompt", "response");
            assertEquals("SKIPPED", r.verdict());
            assertEquals(1.0, r.score());
            assertFalse(r.flagged());
            assertEquals(0L, r.latencyMs());
        }

        @Test
        @DisplayName("returns SKIPPED when originalPrompt is null")
        void nullPromptReturnsSkipped() {
            var r = service.evaluate(null, "some response");
            assertEquals("SKIPPED", r.verdict());
        }

        @Test
        @DisplayName("returns SKIPPED when llmResponse is null")
        void nullResponseReturnsSkipped() {
            var r = service.evaluate("prompt", null);
            assertEquals("SKIPPED", r.verdict());
        }

        @Test
        @DisplayName("returns SKIPPED when llmResponse is blank (spaces)")
        void blankResponseReturnsSkipped() {
            var r = service.evaluate("prompt", "   ");
            assertEquals("SKIPPED", r.verdict());
        }

        @Test
        @DisplayName("returns SKIPPED when llmResponse is empty string")
        void emptyResponseReturnsSkipped() {
            var r = service.evaluate("prompt", "");
            assertEquals("SKIPPED", r.verdict());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GROUNDED verdict paths
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GROUNDED verdict paths")
    class GroundedVerdict {

        @Test
        @DisplayName("returns GROUNDED with score 1.0 when verdict starts with GROUNDED")
        void groundedStartsWith() {
            mockOllamaReturns("GROUNDED - The response accurately cites well-established facts.");
            var r = service.evaluate("What is ML?", "ML is a subset of AI.");
            assertEquals("GROUNDED", r.verdict());
            assertEquals(1.0, r.score());
            assertFalse(r.flagged());
            assertEquals("The response accurately cites well-established facts.", r.reason());
        }

        @Test
        @DisplayName("returns GROUNDED when verdict contains ' GROUNDED -' (space-prefixed, contains branch)")
        void groundedContainsBranch() {
            // upper.startsWith("GROUNDED") = false, upper.contains(" GROUNDED -") = true
            // Use space-prefix to distinguish from "UNGROUNDED -" (which also contains "GROUNDED -")
            mockOllamaReturns("Assessment: GROUNDED - Response is well-grounded.");
            var r = service.evaluate("p", "r");
            assertEquals("GROUNDED", r.verdict());
            assertEquals(1.0, r.score());
        }

        @Test
        @DisplayName("extractReason: reason extracted via newline separator (newline branch)")
        void groundedReasonViaNewline() {
            mockOllamaReturns("GROUNDED\nThis response is fully factual and well-grounded.");
            var r = service.evaluate("p", "r");
            assertEquals("GROUNDED", r.verdict());
            assertEquals("This response is fully factual and well-grounded.", r.reason());
        }

        @Test
        @DisplayName("extractReason: returns prefix when no dash and no newline (prefix fallback branch)")
        void groundedReasonPrefixFallback() {
            mockOllamaReturns("GROUNDED"); // no " - " and no '\n'
            var r = service.evaluate("p", "r");
            assertEquals("GROUNDED", r.verdict());
            assertEquals("GROUNDED", r.reason()); // falls through to return prefix
        }

        @Test
        @DisplayName("extractReason: dash at end of string (dashIdx == length-3) → falls to prefix")
        void groundedDashAtEndOfString() {
            // "GROUNDED - " → ' '=8, '-'=9, ' '=10 → length=11, length-3=8, dashIdx=8 → 8<8 = FALSE
            mockOllamaReturns("GROUNDED - ");
            var r = service.evaluate("p", "r");
            assertEquals("GROUNDED", r.verdict());
            assertEquals("GROUNDED", r.reason()); // dash too close to end → prefix fallback
        }

        @Test
        @DisplayName("extractReason: long reason (>300 chars) is truncated with '...'")
        void groundedLongReasonTruncated() {
            String longReason = "X".repeat(400); // > 300-char truncation limit in extractReason
            mockOllamaReturns("GROUNDED - " + longReason);
            var r = service.evaluate("p", "r");
            assertEquals("GROUNDED", r.verdict());
            assertTrue(r.reason().endsWith("..."), "Reason >300 chars should be truncated with '...'");
            assertEquals(303, r.reason().length()); // 300 + "..."
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PARTIAL verdict paths
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PARTIAL verdict paths")
    class PartialVerdict {

        @Test
        @DisplayName("returns PARTIAL with score 0.65 when verdict starts with PARTIAL")
        void partialStartsWith() {
            mockOllamaReturns("PARTIAL - Some claims are unqualified.");
            var r = service.evaluate("What is AI?", "AI does everything.");
            assertEquals("PARTIAL", r.verdict());
            assertEquals(0.65, r.score());
            assertFalse(r.flagged()); // 0.65 > 0.4 threshold
        }

        @Test
        @DisplayName("returns PARTIAL when verdict contains 'PARTIAL -' (contains branch)")
        void partialContainsBranch() {
            // upper.startsWith("PARTIAL") = false, upper.contains("PARTIAL -") = true
            mockOllamaReturns("Verdict: PARTIAL - mixed accuracy detected.");
            var r = service.evaluate("p", "r");
            assertEquals("PARTIAL", r.verdict());
            assertEquals(0.65, r.score());
        }

        @Test
        @DisplayName("PARTIAL score (0.65) is above minScoreThreshold (0.4) — not flagged")
        void partialAboveThresholdNotFlagged() {
            mockOllamaReturns("PARTIAL - minor unverified claim.");
            var r = service.evaluate("p", "r");
            assertFalse(r.flagged());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UNGROUNDED verdict paths
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("UNGROUNDED verdict paths")
    class UngroundedVerdict {

        @Test
        @DisplayName("returns UNGROUNDED flagged with score 0.2 when verdict starts with UNGROUNDED")
        void ungroundedStartsWith() {
            mockOllamaReturns("UNGROUNDED - The response contains fabricated statistics.");
            var r = service.evaluate("What is the GDP?", "GDP is $999 trillion.");
            assertEquals("UNGROUNDED", r.verdict());
            assertEquals(0.2, r.score());
            assertTrue(r.flagged()); // 0.2 < 0.4 threshold → WARN logged
        }

        @Test
        @DisplayName("returns UNGROUNDED when verdict contains 'UNGROUNDED -' (contains branch)")
        void ungroundedContainsBranch() {
            // upper.startsWith("UNGROUNDED") = false, upper.contains("UNGROUNDED -") = true
            mockOllamaReturns("Result: UNGROUNDED - clearly fabricated data.");
            var r = service.evaluate("p", "r");
            assertEquals("UNGROUNDED", r.verdict());
            assertTrue(r.flagged());
        }

        @Test
        @DisplayName("UNGROUNDED score (0.2) is below threshold (0.4) — warning path")
        void ungroundedBelowThresholdTriggersWarn() {
            mockOllamaReturns("UNGROUNDED - Hallucinated facts.");
            var r = service.evaluate("Tell me something", "Made-up facts here.");
            assertTrue(r.score() < 0.4);
            assertTrue(r.flagged());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UNKNOWN / fallback keyword paths
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("UNKNOWN and fallback keyword paths")
    class UnknownAndFallback {

        @Test
        @DisplayName("returns UNKNOWN for unrecognized verdict — score 0.5, not flagged")
        void unknownForGarbageVerdict() {
            mockOllamaReturns("I am not sure about this response.");
            var r = service.evaluate("p", "r");
            assertEquals("UNKNOWN", r.verdict());
            assertEquals(0.5, r.score());
            assertFalse(r.flagged());
        }

        @Test
        @DisplayName("returns UNGROUNDED when verdict contains HALLUCIN keyword (A-branch of OR)")
        void hallucinKeyword() {
            // contains("HALLUCIN") = true → A=true, short-circuit
            mockOllamaReturns("This shows signs of hallucination.");
            var r = service.evaluate("p", "r");
            assertEquals("UNGROUNDED", r.verdict());
            assertTrue(r.flagged());
        }

        @Test
        @DisplayName("returns UNGROUNDED for FABRICAT keyword (B-branch of OR, A=false)")
        void fabricatKeyword() {
            // contains("HALLUCIN") = false, contains("FABRICAT") = true
            mockOllamaReturns("The data is completely fabricated.");
            var r = service.evaluate("p", "r");
            assertEquals("UNGROUNDED", r.verdict());
            assertTrue(r.flagged());
        }

        @Test
        @DisplayName("returns UNGROUNDED for INACCURAT keyword (C-branch of OR, A=false, B=false)")
        void inaccuratKeyword() {
            // contains("HALLUCIN") = false, contains("FABRICAT") = false, contains("INACCURAT") = true
            mockOllamaReturns("The information provided is clearly inaccurate.");
            var r = service.evaluate("p", "r");
            assertEquals("UNGROUNDED", r.verdict());
            assertTrue(r.flagged());
        }

        @Test
        @DisplayName("returns UNKNOWN when Ollama returns null 'response' value (r==null branch)")
        void nullResponseValue() {
            mockOllamaReturnsNullValue();
            var r = service.evaluate("p", "r");
            assertEquals("UNKNOWN", r.verdict());
            assertEquals(0.5, r.score());
        }

        @Test
        @DisplayName("returns UNKNOWN when Mono is empty (block() returns null → null rawVerdict branch)")
        void emptyMonoProducesNullVerdictUnknown() {
            mockOllamaReturnsEmptyMono();
            var r = service.evaluate("p", "r");
            assertEquals("UNKNOWN", r.verdict());
        }

        @Test
        @DisplayName("returns UNKNOWN with 'Empty verdict' reason when Ollama returns empty string (isBlank branch)")
        void emptyStringVerdictIsBlank() {
            mockOllamaReturns(""); // empty → callOllama returns "" → isBlank() = true
            var r = service.evaluate("p", "r");
            assertEquals("UNKNOWN", r.verdict());
            assertEquals("Empty verdict", r.reason());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Exception / error handling
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("evaluate returns UNKNOWN and 'Checker unavailable' reason on WebClient exception")
    void webClientExceptionReturnsUnknown() {
        when(mockWebClient.post()).thenThrow(new RuntimeException("Connection refused"));
        var r = service.evaluate("prompt", "some LLM response");
        assertEquals("UNKNOWN",             r.verdict());
        assertEquals(0.5,                   r.score());
        assertFalse(r.flagged());
        assertEquals("Checker unavailable", r.reason());
        assertTrue(r.latencyMs() >= 0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Truncation of long inputs (covers both branches of truncate length check)
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Input truncation before Ollama call")
    class Truncation {

        @Test
        @DisplayName("long prompt (>500 chars) is truncated with '...' before being sent")
        void longPromptIsTruncated() {
            String longPrompt = "A".repeat(600); // > 500 → truncated to 500+"..."
            mockOllamaReturns("GROUNDED - truncated prompt accepted.");
            var r = service.evaluate(longPrompt, "response");
            assertEquals("GROUNDED", r.verdict()); // still processes after truncation
        }

        @Test
        @DisplayName("long response (>1000 chars) is truncated with '...' before being sent")
        void longResponseIsTruncated() {
            String longResponse = "B".repeat(1_500); // > 1000 → truncated
            mockOllamaReturns("PARTIAL - truncated response observed.");
            var r = service.evaluate("prompt", longResponse);
            assertEquals("PARTIAL", r.verdict());
        }

        @Test
        @DisplayName("short prompt and response pass through untouched (length <= max branch)")
        void shortInputsUnchanged() {
            mockOllamaReturns("GROUNDED - Factual.");
            var r = service.evaluate("short", "short answer");
            assertEquals("GROUNDED", r.verdict());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GroundednessResult record and factory
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GroundednessResult.skipped() returns correct sentinel values")
    void groundednessResultSkippedFactory() {
        var skipped = GroundednessCheckerService.GroundednessResult.skipped();
        assertEquals("SKIPPED",                        skipped.verdict());
        assertEquals(1.0,                               skipped.score());
        assertFalse(skipped.flagged());
        assertEquals("Checker disabled or input empty", skipped.reason());
        assertEquals(0L,                                skipped.latencyMs());
    }

    @Test
    @DisplayName("GroundednessResult canonical record constructor stores all 5 fields")
    void groundednessResultRecord() {
        var result = new GroundednessCheckerService.GroundednessResult(
                0.7, "GROUNDED", false, "factual response", 42L);
        assertEquals(0.7,              result.score());
        assertEquals("GROUNDED",       result.verdict());
        assertFalse(result.flagged());
        assertEquals("factual response", result.reason());
        assertEquals(42L,              result.latencyMs());
    }

    @Test
    @DisplayName("truncate(null) null-guard branch: private truncate returns empty string for null input")
    void truncateNullReturnsEmptyString() throws Exception {
        // The private truncate() method has a null-guard: if (s == null) return ""
        // It can only be reached via reflection as all public call-sites pass non-null
        var method = GroundednessCheckerService.class
                .getDeclaredMethod("truncate", String.class, int.class);
        method.setAccessible(true);
        String result = (String) method.invoke(service, null, 100);
        assertEquals("", result, "truncate(null, max) should return empty string");
    }

    @Test
    @DisplayName("extractReason dashIdx a=true+b=false branch: dash found but too close to end of string")
    void extractReasonDashAtBoundaryBranchFalse() throws Exception {
        // callOllama() always trims() the verdict, so "GROUNDED - " becomes "GROUNDED -"
        // (no trailing space → " - " pattern not found → covers a=false branch, NOT a=true+b=false).
        // To hit the a=true+b=false branch directly we call extractReason() via reflection
        // with a non-trimmed string where dashIdx >= rawVerdict.length() - 3.
        //
        // "GROUNDED - " has dashIdx=8, length=11, length-3=8, so 8 < 8 = FALSE → a=true, b=false
        var method = GroundednessCheckerService.class
                .getDeclaredMethod("extractReason", String.class, String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(service, "GROUNDED - ", "GROUNDED");
        // Since b=false (dash too close to end) and no newline, should fall through to prefix
        assertEquals("GROUNDED", result,
                "When ' - ' is at boundary (dashIdx >= length-3), reason should fall back to prefix");
    }
}

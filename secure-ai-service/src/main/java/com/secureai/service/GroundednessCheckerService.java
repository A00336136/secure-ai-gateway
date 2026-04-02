package com.secureai.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Groundedness Checker Service — NIST AI 600-1 / OWASP LLM09
 *
 * Implements LLM-as-judge pattern to detect hallucinations in LLM responses.
 * Uses a secondary Ollama inference call with a structured evaluation prompt
 * that asks a small local model to assess whether the response is factually
 * grounded and avoids unverifiable claims.
 *
 * NIST AI 600-1 Risk: "Data Provenance" and "Confabulation" (Hallucination)
 * OWASP LLM09:2025: Misinformation — detects fabricated, inaccurate or misleading content.
 *
 * Scoring:
 *   1.0  — Fully grounded (clear factual statements or appropriate uncertainty)
 *   0.7+ — Mostly grounded (minor unverified claims)
 *   0.4+ — Partially grounded (some hallucination indicators detected)
 *   <0.4 — Likely hallucinated (multiple fabrication signals detected)
 */
@Service
public class GroundednessCheckerService {

    private static final Logger log = LoggerFactory.getLogger(GroundednessCheckerService.class);

    private static final String EVALUATION_PROMPT_TEMPLATE = """
            You are a factual accuracy evaluator. Your task is to assess whether an AI response is grounded and avoids hallucination.

            ORIGINAL QUESTION: %s

            AI RESPONSE TO EVALUATE: %s

            Evaluate the response for the following hallucination signals:
            1. Claims specific facts, statistics, dates, or numbers without qualification
            2. Names specific people, organisations, or sources that may not exist
            3. States uncertain information as definite fact
            4. Contradicts well-established knowledge
            5. Invents plausible-sounding but unverifiable details

            Respond with EXACTLY ONE of these verdicts on the first line, followed by a brief reason:
            GROUNDED - The response is factually careful, appropriately qualified, or clearly states limitations
            PARTIAL   - The response has some unqualified claims but is mostly reasonable
            UNGROUNDED - The response contains likely hallucinated or fabricated information

            Verdict:""";

    private final WebClient webClient;

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.groundedness.model:${ollama.model:llama3.1:8b}}")
    private String groundednessModel;

    @Value("${groundedness.timeout-ms:8000}")
    private int timeoutMs;

    @Value("${groundedness.enabled:true}")
    private boolean enabled;

    @Value("${groundedness.min-score-threshold:0.4}")
    private double minScoreThreshold;

    public GroundednessCheckerService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    /**
     * Evaluate whether an LLM response is factually grounded.
     * Non-blocking: returns a GroundednessResult with score and verdict.
     *
     * @param originalPrompt  the user's original question
     * @param llmResponse     the LLM's response to evaluate
     * @return GroundednessResult with score, verdict, and flags
     */
    @SuppressFBWarnings(value = "VA_FORMAT_STRING_USES_NEWLINE",
            justification = "LLM prompt template uses literal \\n (LF) intentionally for " +
                            "consistent cross-platform prompt formatting — %n would produce " +
                            "platform-specific line separators incompatible with LLM inference")
    public GroundednessResult evaluate(String originalPrompt, String llmResponse) {
        if (!enabled) {
            return GroundednessResult.skipped();
        }
        if (originalPrompt == null || llmResponse == null || llmResponse.isBlank()) {
            return GroundednessResult.skipped();
        }

        long start = System.currentTimeMillis();
        try {
            String evalPrompt = String.format(EVALUATION_PROMPT_TEMPLATE,
                    truncate(originalPrompt, 500),
                    truncate(llmResponse, 1000));

            String verdict = callOllama(evalPrompt);
            long latencyMs = System.currentTimeMillis() - start;

            GroundednessResult result = parseVerdict(verdict, latencyMs);

            if (result.score() < minScoreThreshold) {
                log.warn("GROUNDEDNESS WARNING — score={} verdict={} latency={}ms",
                        result.score(), result.verdict(), latencyMs);
            } else {
                log.debug("Groundedness check: score={} verdict={} latency={}ms",
                        result.score(), result.verdict(), latencyMs);
            }
            return result;

        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - start;
            log.warn("Groundedness checker error (non-blocking): {} — returning UNKNOWN", e.getMessage());
            return new GroundednessResult(0.5, "UNKNOWN", false, "Checker unavailable", latencyMs);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String callOllama(String prompt) {
        Map<String, Object> requestBody = Map.of(
                "model", groundednessModel,
                "prompt", prompt,
                "stream", false,
                "options", Map.of(
                        "num_predict", 100,
                        "temperature", 0.1
                )
        );

        return webClient.post()
                .uri(ollamaBaseUrl + "/api/generate")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .map(response -> {
                    Object r = response.get("response");
                    return r != null ? r.toString().trim() : "UNKNOWN";
                })
                .onErrorReturn("UNKNOWN")
                .block();
    }

    private GroundednessResult parseVerdict(String rawVerdict, long latencyMs) {
        if (rawVerdict == null || rawVerdict.isBlank()) {
            return new GroundednessResult(0.5, "UNKNOWN", false, "Empty verdict", latencyMs);
        }

        String upper = rawVerdict.toUpperCase();

        // NOTE: use " GROUNDED -" (with leading space) to avoid false-positive match on "UNGROUNDED -"
        // "UNGROUNDED -" contains "GROUNDED -" as a substring, so the space-prefixed form is required
        if (upper.startsWith("GROUNDED") || upper.contains(" GROUNDED -")) {
            String reason = extractReason(rawVerdict, "GROUNDED");
            return new GroundednessResult(1.0, "GROUNDED", false, reason, latencyMs);
        } else if (upper.startsWith("PARTIAL") || upper.contains(" PARTIAL -")) {
            String reason = extractReason(rawVerdict, "PARTIAL");
            return new GroundednessResult(0.65, "PARTIAL", false, reason, latencyMs);
        } else if (upper.startsWith("UNGROUNDED") || upper.contains("UNGROUNDED -")) {
            String reason = extractReason(rawVerdict, "UNGROUNDED");
            return new GroundednessResult(0.2, "UNGROUNDED", true, reason, latencyMs);
        } else {
            // Fallback: scan for keywords
            if (upper.contains("HALLUCIN") || upper.contains("FABRICAT") || upper.contains("INACCURAT")) {
                return new GroundednessResult(0.2, "UNGROUNDED", true, truncate(rawVerdict, 200), latencyMs);
            }
            return new GroundednessResult(0.5, "UNKNOWN", false, truncate(rawVerdict, 200), latencyMs);
        }
    }

    private String extractReason(String rawVerdict, String prefix) {
        int dashIdx = rawVerdict.indexOf(" - ");
        if (dashIdx > 0 && dashIdx < rawVerdict.length() - 3) {
            return truncate(rawVerdict.substring(dashIdx + 3).trim(), 300);
        }
        int newlineIdx = rawVerdict.indexOf('\n');
        if (newlineIdx > 0) {
            return truncate(rawVerdict.substring(newlineIdx + 1).trim(), 300);
        }
        return prefix;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Result record
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Immutable result from groundedness evaluation.
     *
     * @param score       0.0 (hallucinated) to 1.0 (fully grounded)
     * @param verdict     "GROUNDED", "PARTIAL", "UNGROUNDED", or "UNKNOWN"
     * @param flagged     true if the response is likely hallucinated (score < threshold)
     * @param reason      brief explanation from the evaluator model
     * @param latencyMs   time taken for the groundedness check
     */
    public record GroundednessResult(
            double score,
            String verdict,
            boolean flagged,
            String reason,
            long latencyMs
    ) {
        /** Use when groundedness checking is disabled or skipped. */
        public static GroundednessResult skipped() {
            return new GroundednessResult(1.0, "SKIPPED", false, "Checker disabled or input empty", 0L);
        }
    }
}

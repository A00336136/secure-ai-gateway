package com.secureai.guardrails;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Layer 1 — NVIDIA NeMo Guardrails v0.10.0
 *
 * Declarative policy enforcement via Colang 2.0 DSL.
 * Evaluates prompts against custom Colang rulesets for:
 *  - Jailbreak prevention
 *  - Topic control
 *  - Content safety flows
 *
 * Uses Mistral 7B (via Ollama) for semantic evaluation.
 * Runs as a Python FastAPI sidecar on port 8001.
 */
@Component
public class NemoGuardrailsClient {

    private static final Logger log = LoggerFactory.getLogger(NemoGuardrailsClient.class);

    @Value("${guardrails.nemo.base-url:http://localhost:8001}")
    private String baseUrl;

    @Value("${guardrails.nemo.timeout-ms:5000}")
    private int timeoutMs;

    @Value("${guardrails.nemo.enabled:true}")
    private boolean enabled;

    private final RestTemplate restTemplate;

    public NemoGuardrailsClient() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Evaluate a prompt against NeMo Guardrails Colang policies.
     *
     * @param prompt the user's input prompt
     * @return Mono emitting the guardrails result
     */
    public Mono<GuardrailsResult> evaluate(String prompt) {
        if (!enabled) {
            return Mono.just(GuardrailsResult.pass("NeMo", 0));
        }

        return Mono.fromCallable(() -> {
            long start = System.currentTimeMillis();
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                Map<String, Object> body = Map.of(
                        "messages", new Object[]{Map.of("role", "user", "content", prompt)}
                );

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
                ResponseEntity<String> response = restTemplate.postForEntity(
                        baseUrl + "/v1/chat/completions", entity, String.class);

                long latency = System.currentTimeMillis() - start;

                if (response.getStatusCode().is2xxSuccessful()) {
                    String responseBody = response.getBody();
                    // NeMo returns 200 with refusal text when Colang blocks
                    // Check response content for block indicators
                    if (responseBody != null) {
                        String lower = responseBody.toLowerCase();
                        if (lower.contains("cannot comply") || lower.contains("cannot provide")
                                || lower.contains("cannot assist") || lower.contains("not able to")
                                || lower.contains("i'm sorry") || lower.contains("security guardrails")
                                || lower.contains("cannot help") || lower.contains("illegal")
                                || lower.contains("harmful") || lower.contains("flagged")
                                || lower.contains("been blocked") || lower.contains("been logged")
                                || lower.contains("content safety") || lower.contains("audit purposes")
                                || lower.contains("not allowed") || lower.contains("refuse")
                                || lower.contains("not permitted") || lower.contains("policy violation")) {
                            log.warn("NeMo Guardrails BLOCKED prompt (content refusal) — {}ms", latency);
                            return GuardrailsResult.block("NeMo", "colang_policy_violation", null, latency);
                        }
                    }
                    log.debug("NeMo Guardrails PASS — {}ms", latency);
                    return GuardrailsResult.pass("NeMo", latency);
                }

                // 422 = content blocked by Colang policy (legacy check)
                if (response.getStatusCode().value() == 422) {
                    log.warn("NeMo Guardrails BLOCKED prompt (422) — {}ms", latency);
                    return GuardrailsResult.block("NeMo", "colang_policy_violation", null, latency);
                }

                log.warn("NeMo Guardrails unexpected status {} — {}ms",
                        response.getStatusCode().value(), latency);
                return GuardrailsResult.pass("NeMo", latency);

            } catch (RestClientException e) {
                long latency = System.currentTimeMillis() - start;
                log.error("NeMo Guardrails unreachable ({}ms): {}", latency, sanitizeLog(e.getMessage()));
                // Fail-CLOSED: if NeMo is unreachable, block the request
                return GuardrailsResult.block("NeMo", "service_unavailable", null, latency);
            }
        });
    }

    public boolean isHealthy() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    baseUrl + "/", String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (RestClientException e) {
            return false;
        }
    }

    private static String sanitizeLog(String value) {
        if (value == null) return "(null)";
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }
}

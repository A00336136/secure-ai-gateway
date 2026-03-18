package com.secureai.guardrails;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Layer 3 — Microsoft Presidio v2.2
 *
 * Enterprise-grade PII detection across 50+ entity types:
 *  - PERSON, EMAIL, PHONE, CREDIT_CARD (Luhn validated), IBAN_CODE (ISO 13616 checksum)
 *  - MEDICAL_RECORD, NHS_NUMBER, PASSPORT, DRIVER_LICENSE, IP_ADDRESS
 *  - DATE_TIME, NRP, LOCATION, and 40+ more
 *
 * Fully offline, Apache 2.0 licensed. Runs as a Python sidecar on port 5002.
 * Supports 16+ languages for multi-language PII detection.
 * Directly strengthens GDPR Article 25 compliance.
 */
@Component
public class PresidioClient {

    private static final Logger log = LoggerFactory.getLogger(PresidioClient.class);

    @Value("${guardrails.presidio.base-url:http://localhost:5002}")
    private String baseUrl;

    @Value("${guardrails.presidio.timeout-ms:3000}")
    private int timeoutMs;

    @Value("${guardrails.presidio.enabled:true}")
    private boolean enabled;

    @Value("${guardrails.presidio.language:en}")
    private String language;

    @Value("${guardrails.presidio.min-score:0.6}")
    private double minScore;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public PresidioClient() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Analyze text for PII entities using Microsoft Presidio.
     *
     * @param text the text to analyze
     * @return Mono emitting the guardrails result (blocked if PII found)
     */
    public Mono<GuardrailsResult> evaluate(String text) {
        if (!enabled) {
            return Mono.just(GuardrailsResult.pass("Presidio", 0));
        }

        return Mono.fromCallable(() -> {
            long start = System.currentTimeMillis();
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                Map<String, Object> body = Map.of(
                        "text", text,
                        "language", language,
                        "score_threshold", minScore
                );

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
                ResponseEntity<String> response = restTemplate.postForEntity(
                        baseUrl + "/analyze", entity, String.class);

                long latency = System.currentTimeMillis() - start;

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    List<PresidioEntity> entities = objectMapper.readValue(
                            response.getBody(), new TypeReference<List<PresidioEntity>>() {});

                    if (entities.isEmpty()) {
                        log.debug("Presidio: no PII detected — {}ms", latency);
                        return GuardrailsResult.pass("Presidio", latency);
                    }

                    String piiTypes = entities.stream()
                            .map(e -> e.entityType)
                            .distinct()
                            .collect(Collectors.joining(", "));

                    double maxScore = entities.stream()
                            .mapToDouble(e -> e.score)
                            .max()
                            .orElse(0.0);

                    log.warn("Presidio detected PII [{}] (max score: {}) — {}ms",
                            piiTypes, maxScore, latency);
                    return GuardrailsResult.block("Presidio", piiTypes, maxScore, latency);
                }

                long latency2 = System.currentTimeMillis() - start;
                return GuardrailsResult.pass("Presidio", latency2);

            } catch (RestClientException e) {
                long latency = System.currentTimeMillis() - start;
                log.error("Presidio unreachable ({}ms): {}", latency, sanitizeLog(e.getMessage()));
                // Fail-CLOSED
                return GuardrailsResult.block("Presidio", "service_unavailable", null, latency);
            } catch (Exception e) {
                long latency = System.currentTimeMillis() - start;
                log.error("Presidio parse error ({}ms): {}", latency, sanitizeLog(e.getMessage()));
                return GuardrailsResult.block("Presidio", "parse_error", null, latency);
            }
        });
    }

    public boolean isHealthy() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    baseUrl + "/health", String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (RestClientException e) {
            return false;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class PresidioEntity {
        public String entityType;
        public double score;
        public int start;
        public int end;

        // Jackson setters
        public void setEntity_type(String entityType) { this.entityType = entityType; }
        public void setScore(double score) { this.score = score; }
        public void setStart(int start) { this.start = start; }
        public void setEnd(int end) { this.end = end; }
    }

    private static String sanitizeLog(String value) {
        if (value == null) return "(null)";
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }
}

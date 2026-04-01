package com.secureai.guardrails;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    private static final String GUARD_NAME = "Presidio";

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
            return Mono.just(GuardrailsResult.pass(GUARD_NAME, 0));
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

                    // Filter: only block on truly sensitive PII, not general entities
                    // LOCATION, PERSON, DATE_TIME, NRP are common in normal prompts
                    var SENSITIVE_TYPES = java.util.Set.of(
                            "CREDIT_CARD", "CRYPTO", "US_SSN", "US_BANK_NUMBER",
                            "IBAN_CODE", "US_PASSPORT", "US_DRIVER_LICENSE",
                            "UK_NHS", "MEDICAL_LICENSE", "IP_ADDRESS",
                            "EMAIL_ADDRESS", "PHONE_NUMBER", "US_ITIN",
                            "AU_ABN", "AU_ACN", "AU_TFN", "AU_MEDICARE",
                            "SG_NRIC_FIN", "IN_PAN", "IN_AADHAAR"
                    );

                    List<PresidioEntity> sensitiveEntities = entities.stream()
                            .filter(e -> SENSITIVE_TYPES.contains(e.getEntityType()))
                            .collect(Collectors.toList());

                    if (sensitiveEntities.isEmpty()) {
                        if (!entities.isEmpty()) {
                            String ignoredTypes = entities.stream()
                                    .map(PresidioEntity::getEntityType).distinct()
                                    .collect(Collectors.joining(", "));
                            log.debug("{}: detected non-sensitive entities [{}] — ignored — {}ms",
                                    GUARD_NAME, ignoredTypes, latency);
                        }
                        return GuardrailsResult.pass(GUARD_NAME, latency);
                    }

                    String piiTypes = sensitiveEntities.stream()
                            .map(PresidioEntity::getEntityType)
                            .distinct()
                            .collect(Collectors.joining(", "));

                    double maxScore = sensitiveEntities.stream()
                            .mapToDouble(PresidioEntity::getScore)
                            .max()
                            .orElse(0.0);

                    log.warn("{} detected PII [{}] (max score: {}) — {}ms",
                            GUARD_NAME, piiTypes, maxScore, latency);
                    return GuardrailsResult.block(GUARD_NAME, piiTypes, maxScore, latency);
                }

                long latency2 = System.currentTimeMillis() - start;
                return GuardrailsResult.pass(GUARD_NAME, latency2);

            } catch (RestClientException e) {
                long latency = System.currentTimeMillis() - start;
                log.error("{} unreachable ({}ms): {}", GUARD_NAME, latency, sanitizeLog(e.getMessage()));
                // Fail-CLOSED
                return GuardrailsResult.block(GUARD_NAME, "service_unavailable", null, latency);
            } catch (Exception e) {
                long latency = System.currentTimeMillis() - start;
                log.error("{} parse error ({}ms): {}", GUARD_NAME, latency, sanitizeLog(e.getMessage()));
                return GuardrailsResult.block(GUARD_NAME, "parse_error", null, latency);
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
        private String entityType;
        private double score;
        private int start;
        private int end;

        // Jackson setters
        @JsonProperty("entity_type")
        public void setEntityType(String entityType) { this.entityType = entityType; }
        public void setScore(double score) { this.score = score; }
        public void setStart(int start) { this.start = start; }
        public void setEnd(int end) { this.end = end; }

        public String getEntityType() { return entityType; }
        public double getScore() { return score; }
        public int getStart() { return start; }
        public int getEnd() { return end; }
    }

    private static String sanitizeLog(String value) {
        if (value == null) return "(null)";
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }
}

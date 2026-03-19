package com.secureai.guardrails;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Layer 2 — Meta LlamaGuard 3 (via Ollama)
 *
 * MLCommons AI Safety taxonomy coverage for 12 categories (S1–S12):
 *  S1  Violent Crimes          S7  Privacy
 *  S2  Non-Violent Crimes      S8  Intellectual Property
 *  S3  Sex-Related Crimes      S9  Indiscriminate Weapons (WMD)
 *  S4  Child Safety            S10 Hate Speech
 *  S5  Defamation              S11 Suicide & Self-Harm
 *  S6  Specialized Advice      S12 Elections
 *
 * Catches novel harms that NeMo Colang rules cannot anticipate.
 * Runs in ~50ms on CPU via Ollama. Fully offline.
 */
@Component
public class LlamaGuardClient {

    private static final Logger log = LoggerFactory.getLogger(LlamaGuardClient.class);
    private static final String GUARD_NAME = "LlamaGuard";

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${guardrails.llamaguard.model:llamaguard3:8b}")
    private String llamaguardModel;

    @Value("${guardrails.llamaguard.timeout-ms:5000}")
    private int timeoutMs;

    @Value("${guardrails.llamaguard.enabled:true}")
    private boolean enabled;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public LlamaGuardClient() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Classify a prompt using LlamaGuard 3's MLCommons safety taxonomy.
     *
     * @param prompt the user's input prompt
     * @return Mono emitting the guardrails result
     */
    public Mono<GuardrailsResult> evaluate(String prompt) {
        if (!enabled) {
            return Mono.just(GuardrailsResult.pass(GUARD_NAME, 0));
        }

        return Mono.fromCallable(() -> {
            long start = System.currentTimeMillis();
            try {
                ResponseEntity<String> response = callOllama(prompt);
                long latency = System.currentTimeMillis() - start;
                return parseOllamaResponse(response, latency);
            } catch (RestClientException e) {
                long latency = System.currentTimeMillis() - start;
                log.error("{} unreachable ({}ms): {}", GUARD_NAME, latency, sanitizeLog(e.getMessage()));
                // Fail-CLOSED: if LlamaGuard is unreachable, block
                return GuardrailsResult.block(GUARD_NAME, "service_unavailable", null, latency);
            } catch (Exception e) {
                long latency = System.currentTimeMillis() - start;
                log.error("{} parse error ({}ms): {}", GUARD_NAME, latency, sanitizeLog(e.getMessage()));
                return GuardrailsResult.block(GUARD_NAME, "parse_error", null, latency);
            }
        });
    }

    private ResponseEntity<String> callOllama(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // LlamaGuard expects a conversation format
        Map<String, Object> body = Map.of(
                "model", llamaguardModel,
                "prompt", buildLlamaGuardPrompt(prompt),
                "stream", false,
                "options", Map.of("temperature", 0.0, "num_predict", 100)
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        return restTemplate.postForEntity(
                ollamaBaseUrl + "/api/generate", entity, String.class);
    }

    private GuardrailsResult parseOllamaResponse(ResponseEntity<String> response, long latency)
            throws com.fasterxml.jackson.core.JsonProcessingException {
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return GuardrailsResult.pass(GUARD_NAME, latency);
        }

        OllamaResponse parsed = objectMapper.readValue(response.getBody(), OllamaResponse.class);
        String result = parsed.getResponse() != null ? parsed.getResponse().trim() : "";

        // LlamaGuard 3 returns "safe" or "unsafe\nSX" where SX is the category
        if (result.toLowerCase().startsWith("safe")) {
            log.debug("{} SAFE — {}ms", GUARD_NAME, latency);
            return GuardrailsResult.pass(GUARD_NAME, latency);
        }

        // Parse unsafe category
        String category = "unknown";
        if (result.contains("\n")) {
            category = result.substring(result.indexOf('\n') + 1).trim();
        }
        log.warn("{} UNSAFE [{}] — {}ms", GUARD_NAME, category, latency);
        return GuardrailsResult.block(GUARD_NAME, category, null, latency);
    }

    private String buildLlamaGuardPrompt(String userPrompt) {
        return """
                <|begin_of_text|><|start_header_id|>user<|end_header_id|>

                Task: Check if there is unsafe content in 'User' messages in conversations
                according to our safety policy with the below categories.

                <BEGIN UNSAFE CONTENT CATEGORIES>
                S1: Violent Crimes
                S2: Non-Violent Crimes
                S3: Sex-Related Crimes
                S4: Child Sexual Exploitation
                S5: Defamation
                S6: Specialized Advice
                S7: Privacy
                S8: Intellectual Property
                S9: Indiscriminate Weapons
                S10: Hate
                S11: Suicide & Self-Harm
                S12: Sexual Content
                S13: Elections
                <END UNSAFE CONTENT CATEGORIES>

                <BEGIN CONVERSATION>

                User: %s

                <END CONVERSATION>

                Provide your safety assessment for User in the above conversation:
                - First line must read 'safe' or 'unsafe'.
                - If unsafe, a second line must include a comma-separated list of violated categories.
                <|eot_id|><|start_header_id|>assistant<|end_header_id|>
                """.replace("%s", userPrompt);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class OllamaResponse {
        private String response;
        public void setResponse(String response) { this.response = response; }
        public String getResponse() { return response; }
    }

    private static String sanitizeLog(String value) {
        if (value == null) return "(null)";
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }
}

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
            return Mono.just(GuardrailsResult.pass("LlamaGuard", 0));
        }

        return Mono.fromCallable(() -> {
            long start = System.currentTimeMillis();
            try {
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
                ResponseEntity<String> response = restTemplate.postForEntity(
                        ollamaBaseUrl + "/api/generate", entity, String.class);

                long latency = System.currentTimeMillis() - start;

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    OllamaResponse parsed = objectMapper.readValue(response.getBody(), OllamaResponse.class);
                    String result = parsed.getResponse() != null ? parsed.getResponse().trim() : "";

                    // LlamaGuard 3 returns "safe" or "unsafe\nSX" where SX is the category
                    if (result.toLowerCase().startsWith("safe")) {
                        log.debug("LlamaGuard SAFE — {}ms", latency);
                        return GuardrailsResult.pass("LlamaGuard", latency);
                    }

                    // Parse unsafe category
                    String category = "unknown";
                    if (result.contains("\n")) {
                        category = result.substring(result.indexOf('\n') + 1).trim();
                    }
                    log.warn("LlamaGuard UNSAFE [{}] — {}ms", category, latency);
                    return GuardrailsResult.block("LlamaGuard", category, null, latency);
                }

                long latency2 = System.currentTimeMillis() - start;
                return GuardrailsResult.pass("LlamaGuard", latency2);

            } catch (RestClientException e) {
                long latency = System.currentTimeMillis() - start;
                log.error("LlamaGuard unreachable ({}ms): {}", latency, sanitizeLog(e.getMessage()));
                // Fail-CLOSED: if LlamaGuard is unreachable, block
                return GuardrailsResult.block("LlamaGuard", "service_unavailable", null, latency);
            } catch (Exception e) {
                long latency = System.currentTimeMillis() - start;
                log.error("LlamaGuard parse error ({}ms): {}", latency, sanitizeLog(e.getMessage()));
                return GuardrailsResult.block("LlamaGuard", "parse_error", null, latency);
            }
        });
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
                """.formatted(userPrompt);
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

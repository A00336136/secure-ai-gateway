package com.secureai.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;

import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Ollama Local LLM Client
 *
 * Supports LLaMA 3.1 8B, Mistral 7B, and any Ollama-compatible model.
 * All data stays on-device — zero cloud dependency, full data sovereignty.
 *
 * API: POST /api/generate (non-streaming)
 *
 * SpotBugs fixes applied:
 *  - CRLF_INJECTION_LOGS (lines 61, 90, 95)  : user-derived values wrapped in sanitizeLog()
 *  - REC_CATCH_EXCEPTION  (line 107)          : isHealthy() now catches RestClientException

 *  - UUF_UNUSED_PUBLIC_OR_PROTECTED_FIELD     : removed unread fields (model, done,
 *                                               total_duration, eval_count) from OllamaResponse
 *  - UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD  : response + error are now private with
 *                                               explicit setters so Jackson can populate them
 *                                               without SpotBugs treating them as unwritten
 */
@Service
public class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    @Value("${ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${ollama.model:llama3.1:8b}")
    private String model;

    @Value("${ollama.timeout-seconds:120}")
    private int timeoutSeconds;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public OllamaClient() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Generate a response from the local LLM.
     * @param prompt user's prompt
     * @return LLM text response
     * @throws OllamaException if the model is unavailable or returns an error
     */
    public String generateResponse(String prompt) {
        return generateResponse(prompt, null);
    }

    /**
     * Generate a response with a system prompt (used by ReAct agent).
     */
    public String generateResponse(String prompt, String systemPrompt) {
        String url = baseUrl + "/api/generate";

        // FIX CRLF_INJECTION_LOGS (was line 61): prompt is user-supplied — sanitize before
        // logging. Truncate first to limit log volume, then sanitize so CR/LF cannot forge lines.
        String safePromptPreview = sanitizeLog(
                prompt != null && prompt.length() > 80 ? prompt.substring(0, 80) + "..." : prompt);
        log.debug("Sending prompt to Ollama model '{}': {}...", model, safePromptPreview);

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("prompt", prompt);
            requestBody.put("stream", false);
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                requestBody.put("system", systemPrompt);
            }
            requestBody.put("options", Map.of(
                "temperature", 0.7,
                "top_p", 0.9,
                "num_predict", 2048
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseOllamaResponse(response.getBody());
            }
            throw new OllamaException("Ollama returned status: " + response.getStatusCode());

        } catch (ResourceAccessException e) {
            // FIX CRLF_INJECTION_LOGS (was line 90): e.getMessage() may echo user-controlled
            // network data — sanitize to prevent forged log lines.
            log.error("Cannot connect to Ollama at {}: {}", baseUrl, sanitizeLog(e.getMessage()));
            throw new OllamaException(
                    "Ollama LLM is not available. Please ensure Ollama is running: ollama serve", e);
        } catch (OllamaException e) {
            throw e;
        } catch (Exception e) {
            // FIX CRLF_INJECTION_LOGS (was line 95): same reason — sanitize getMessage().
            log.error("Unexpected error calling Ollama: {}", sanitizeLog(e.getMessage()), e);
            throw new OllamaException(
                    "Unexpected error communicating with Ollama: " + e.getMessage(), e);
        }
    }

    /**
     * Check if Ollama is reachable.
     *
     * FIX REC_CATCH_EXCEPTION (was line 107): the original caught broad {@code Exception},
     * which SpotBugs flags because RestTemplate only declares unchecked exceptions and
     * {@code Exception} is never actually thrown as a checked type here.
     * We now catch only the two concrete unchecked types RestTemplate actually throws:
    
         *  - {@link RestClientException} — covers both network failures (ResourceAccessException)
     *    and HTTP-level errors (4xx/5xx); ResourceAccessException is a subclass so one
     *    catch clause handles both without violating Javas multi-catch subtype restriction.
     * Programming errors (NPE etc.) are intentionally allowed to propagate so they are
     * not silently swallowed.
     */
    public boolean isHealthy() {
        try {
            ResponseEntity<String> response =
                    restTemplate.getForEntity(baseUrl + "/api/tags", String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (RestClientException e) {
            log.warn("Ollama health check failed: {}", sanitizeLog(e.getMessage()));
            return false;
        }
    }

    public String getModel() {
        return model;
    }

    // ─────────────────────────────────────────────────────────────────────────

    private String parseOllamaResponse(String body) throws Exception {
        OllamaResponse parsed = objectMapper.readValue(body, OllamaResponse.class);
        if (parsed.getResponse() != null && !parsed.getResponse().isBlank()) {
            return parsed.getResponse().trim();
        }
        if (parsed.getError() != null) {
            throw new OllamaException("Ollama error: " + parsed.getError());
        }
        throw new OllamaException("Empty response from Ollama");
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Deserialization DTO for Ollama's /api/generate response.
     *
     * FIX UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD: {@code response} and {@code error}
     * were public fields only written by Jackson's reflection — SpotBugs cannot see that
     * path and reports them as "unwritten". Making them private with explicit setters gives
     * Jackson the same access via standard bean convention while satisfying the analyser.
     *
     * FIX UUF_UNUSED_PUBLIC_OR_PROTECTED_FIELD: {@code model}, {@code done},
     * {@code total_duration}, and {@code eval_count} existed only for deserialisation but
     * were never read by application code. They are removed here; {@code @JsonIgnoreProperties
     * (ignoreUnknown = true)} ensures Ollama's extra JSON keys are silently discarded.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class OllamaResponse {

        private String response;
        private String error;

        // Standard bean setters — required by Jackson when fields are private
        public void setResponse(String response) { this.response = response; }
        public void setError(String error)       { this.error = error; }

        public String getResponse() { return response; }
        public String getError()    { return error; }
    }

    // ─────────────────────────────────────────────────────────────────────────

    public static class OllamaException extends RuntimeException {
        public OllamaException(String message) { super(message); }
        public OllamaException(String message, Throwable cause) { super(message, cause); }
    }

    // ─────────────────────────────────────────────────────────────────────────

    /** Strips CR and LF to prevent CRLF injection in log messages. */
    private static String sanitizeLog(String value) {
        if (value == null) return "(null)";
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }
}

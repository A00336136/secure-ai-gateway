package com.secureai.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
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
        log.debug("Sending prompt to Ollama model '{}': {}...",
                model, prompt.length() > 80 ? prompt.substring(0, 80) + "..." : prompt);

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
            log.error("Cannot connect to Ollama at {}: {}", baseUrl, e.getMessage());
            throw new OllamaException("Ollama LLM is not available. Please ensure Ollama is running: ollama serve", e);
        } catch (OllamaException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error calling Ollama: {}", e.getMessage(), e);
            throw new OllamaException("Unexpected error communicating with Ollama: " + e.getMessage(), e);
        }
    }

    /**
     * Check if Ollama is reachable.
     */
    public boolean isHealthy() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(baseUrl + "/api/tags", String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    public String getModel() {
        return model;
    }

    // ─────────────────────────────────────────────────────────────────────────

    private String parseOllamaResponse(String body) throws Exception {
        OllamaResponse parsed = objectMapper.readValue(body, OllamaResponse.class);
        if (parsed.response != null && !parsed.response.isBlank()) {
            return parsed.response.trim();
        }
        if (parsed.error != null) {
            throw new OllamaException("Ollama error: " + parsed.error);
        }
        throw new OllamaException("Empty response from Ollama");
    }

    // ─────────────────────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class OllamaResponse {
        public String model;
        public String response;
        public boolean done;
        public String error;
        public Long total_duration;
        public Long eval_count;
    }

    public static class OllamaException extends RuntimeException {
        public OllamaException(String message) { super(message); }
        public OllamaException(String message, Throwable cause) { super(message, cause); }
    }
}

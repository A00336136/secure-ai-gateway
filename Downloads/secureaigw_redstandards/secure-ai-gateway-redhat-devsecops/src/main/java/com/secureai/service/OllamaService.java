package com.secureai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureai.exception.SecureAiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for communicating with Ollama AI service.
 * Implements retry logic, timeout handling, and proper error management.
 */
@Slf4j
@Service
public class OllamaService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.model:mistral:7b}")
    private String model;

    @Value("${ollama.timeout:30000}")
    private int timeout;

    @Value("${ollama.max-retries:3}")
    private int maxRetries;

    public OllamaService(RestTemplateBuilder restTemplateBuilder, ObjectMapper objectMapper) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(5000))
                .setReadTimeout(Duration.ofMillis(30000))
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * Generate a response from the AI model for the given prompt.
     *
     * @param prompt the user prompt
     * @return the AI-generated response
     * @throws SecureAiException if communication fails
     */
    public String generateResponse(String prompt) {
        log.debug("Generating response for prompt of length: {}", prompt.length());

        String url = ollamaBaseUrl + "/api/generate";
        int attempt = 0;
        Exception lastException = null;

        while (attempt < maxRetries) {
            attempt++;
            try {
                String requestBody = buildRequestBody(prompt);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

                log.debug("Attempt {}/{} - Sending request to Ollama", attempt, maxRetries);
                ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    String extractedResponse = extractResponse(response.getBody());
                    log.info("Successfully generated response from Ollama (attempt {})", attempt);
                    return extractedResponse;
                }

            } catch (RestClientException e) {
                lastException = e;
                log.warn("Attempt {}/{} failed: {}", attempt, maxRetries, e.getMessage());
                
                if (attempt < maxRetries) {
                    try {
                        // Exponential backoff
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new SecureAiException("Request interrupted", ie);
                    }
                }
            }
        }

        log.error("Failed to communicate with Ollama after {} attempts", maxRetries);
        throw new SecureAiException(
                "Failed to communicate with AI service after " + maxRetries + " attempts",
                lastException
        );
    }

    /**
     * Build the JSON request body for Ollama API.
     *
     * @param prompt the user prompt
     * @return JSON request body as string
     */
    private String buildRequestBody(String prompt) {
        try {
            Map<String, Object> requestMap = new HashMap<>();
            requestMap.put("model", model);
            requestMap.put("prompt", prompt);
            requestMap.put("stream", false);
            
            return objectMapper.writeValueAsString(requestMap);
        } catch (Exception e) {
            log.error("Failed to build request body", e);
            throw new SecureAiException("Failed to build request", e);
        }
    }

    /**
     * Extract the response text from Ollama's JSON response.
     *
     * @param responseBody the raw response body
     * @return the extracted response text
     */
    private String extractResponse(String responseBody) {
        try {
            JsonNode rootNode = objectMapper.readTree(responseBody);
            
            if (rootNode.has("response")) {
                return rootNode.get("response").asText();
            } else if (rootNode.has("message")) {
                JsonNode messageNode = rootNode.get("message");
                if (messageNode.has("content")) {
                    return messageNode.get("content").asText();
                }
            }
            
            log.warn("Unexpected response format from Ollama: {}", responseBody);
            return responseBody;
            
        } catch (Exception e) {
            log.error("Failed to parse Ollama response", e);
            // Return raw response if parsing fails
            return responseBody;
        }
    }

    /**
     * Check if Ollama service is available.
     *
     * @return true if service is reachable, false otherwise
     */
    public boolean isAvailable() {
        try {
            String url = ollamaBaseUrl + "/api/tags";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Ollama service is not available: {}", e.getMessage());
            return false;
        }
    }
}

package com.secureai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class OllamaClient {

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.model:mistral:7b}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();

    public String generateResponse(String prompt) {
        String url = ollamaBaseUrl + "/api/generate";

        String escapedPrompt = prompt.replace("\"", "\\\"");
        String requestBody = String.format(
                "{\"model\":\"%s\",\"prompt\":\"%s\",\"stream\":false}",
                model, escapedPrompt
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response =
                    restTemplate.postForEntity(url, entity, String.class);
            return response.getBody();
        } catch (Exception e) {
            throw new RuntimeException("Failed to communicate with Ollama: " + e.getMessage(), e);
        }
    }
}

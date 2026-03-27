package com.secureai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OllamaClient Unit Tests")
class OllamaClientTest {

    @Mock
    private RestTemplate restTemplate;

    private OllamaClient ollamaClient;

    @BeforeEach
    void setUp() {
        ollamaClient = new OllamaClient();
        // Use ReflectionTestUtils or similar if RestTemplate is used internally,
        // or refactor OllamaClient to accept RestTemplate in constructor.
    }

    @Test
    @DisplayName("generateResponse should return text from Ollama API")
    void generateResponseShouldReturnText() {
        String expectedResponse = "Ollama's answer";
        String mockJsonResponse = "{\"response\": \"" + expectedResponse + "\"}";
        
        // Mock the RestTemplate inside the class using ReflectionTestUtils
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "restTemplate", restTemplate);
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "baseUrl", "http://localhost:11434");

        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new org.springframework.http.ResponseEntity<>(mockJsonResponse, org.springframework.http.HttpStatus.OK));

        String result = ollamaClient.generateResponse("test prompt");

        assertEquals(expectedResponse, result);
        verify(restTemplate).postForEntity(contains("/api/generate"), any(), eq(String.class));
    }

    @Test
    @DisplayName("isHealthy should return true if API responds")
    void isHealthyShouldReturnTrue() {
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "restTemplate", restTemplate);
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "baseUrl", "http://localhost:11434");

        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(new org.springframework.http.ResponseEntity<>("OK", org.springframework.http.HttpStatus.OK));

        assertTrue(ollamaClient.isHealthy());
        verify(restTemplate).getForEntity(contains("/api/tags"), eq(String.class));
    }

    @Test
    @DisplayName("generateResponse should throw exception on API error")
    void generateResponseShouldThrowOnApiError() {
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "restTemplate", restTemplate);
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "baseUrl", "http://localhost:11434");

        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new org.springframework.http.ResponseEntity<>("Server Error", org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(RuntimeException.class, () -> ollamaClient.generateResponse("test"));
    }

    @Test
    @DisplayName("generateResponse should throw on RestClientException")
    void generateResponseShouldThrowOnRestError() {
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "restTemplate", restTemplate);
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "baseUrl", "http://localhost:11434");

        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new org.springframework.web.client.RestClientException("Connect refused"));

        assertThrows(RuntimeException.class, () -> ollamaClient.generateResponse("test"));
    }

    @Test
    @DisplayName("generateResponse should throw exception on Ollama error response")
    void generateResponseShouldThrowOnOllamaError() {
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "restTemplate", restTemplate);
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "baseUrl", "http://localhost:11434");
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "objectMapper", new com.fasterxml.jackson.databind.ObjectMapper());

        String mockErrorResponse = "{\"error\": \"model not found\"}";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new org.springframework.http.ResponseEntity<>(mockErrorResponse, org.springframework.http.HttpStatus.OK));

        assertThrows(RuntimeException.class, () -> ollamaClient.generateResponse("test"));
    }

    @Test
    @DisplayName("generateResponse should throw on ResourceAccessException")
    void generateResponseShouldThrowOnResourceAccess() {
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "restTemplate", restTemplate);
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "baseUrl", "http://localhost:11434");

        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("Timeout"));

        assertThrows(RuntimeException.class, () -> ollamaClient.generateResponse("test"));
    }

    @Test
    @DisplayName("generateResponse should throw on empty response body")
    void generateResponseShouldThrowOnEmptyBody() {
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "restTemplate", restTemplate);
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "baseUrl", "http://localhost:11434");
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "objectMapper", new com.fasterxml.jackson.databind.ObjectMapper());

        String mockEmptyResponse = "{}";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new org.springframework.http.ResponseEntity<>(mockEmptyResponse, org.springframework.http.HttpStatus.OK));

        assertThrows(RuntimeException.class, () -> ollamaClient.generateResponse("test"));
    }

    @Test
    @DisplayName("isHealthy should return false on error")
    void isHealthyShouldReturnFalseOnError() {
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "restTemplate", restTemplate);
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "baseUrl", "http://localhost:11434");

        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenThrow(new org.springframework.web.client.RestClientException("Connect refused"));

        assertFalse(ollamaClient.isHealthy());
    }

    @Test
    @DisplayName("getModel should return model name")
    void getModelShouldReturnName() {
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "model", "test-model");
        assertEquals("test-model", ollamaClient.getModel());
    }

    @Test
    @DisplayName("generateResponse with system prompt should include system in request body")
    void generateResponseWithSystemPrompt() {
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "restTemplate", restTemplate);
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "baseUrl", "http://localhost:11434");

        String mockJsonResponse = "{\"response\": \"Agent answer\"}";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new org.springframework.http.ResponseEntity<>(mockJsonResponse, org.springframework.http.HttpStatus.OK));

        String result = ollamaClient.generateResponse("What is AI?", "You are a helpful assistant");
        assertEquals("Agent answer", result);
    }

    @Test
    @DisplayName("generateResponse with blank system prompt should not include system key")
    void generateResponseWithBlankSystemPrompt() {
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "restTemplate", restTemplate);
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "baseUrl", "http://localhost:11434");

        String mockJsonResponse = "{\"response\": \"No system prompt answer\"}";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new org.springframework.http.ResponseEntity<>(mockJsonResponse, org.springframework.http.HttpStatus.OK));

        String result = ollamaClient.generateResponse("What is AI?", "   ");
        assertEquals("No system prompt answer", result);
    }

    @Test
    @DisplayName("generateResponse with long prompt should truncate for logging")
    void generateResponseWithLongPrompt() {
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "restTemplate", restTemplate);
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "baseUrl", "http://localhost:11434");

        String longPrompt = "A".repeat(200);
        String mockJsonResponse = "{\"response\": \"Long prompt answer\"}";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new org.springframework.http.ResponseEntity<>(mockJsonResponse, org.springframework.http.HttpStatus.OK));

        String result = ollamaClient.generateResponse(longPrompt);
        assertEquals("Long prompt answer", result);
    }

    @Test
    @DisplayName("generateResponse with null prompt should handle sanitization")
    void generateResponseWithNullPrompt() {
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "restTemplate", restTemplate);
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "baseUrl", "http://localhost:11434");

        String mockJsonResponse = "{\"response\": \"null prompt handled\"}";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new org.springframework.http.ResponseEntity<>(mockJsonResponse, org.springframework.http.HttpStatus.OK));

        String result = ollamaClient.generateResponse(null);
        assertEquals("null prompt handled", result);
    }

    @Test
    @DisplayName("generateResponse single-arg should delegate to two-arg with null system prompt")
    void generateResponseSingleArgDelegatesToTwoArg() {
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "restTemplate", restTemplate);
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "baseUrl", "http://localhost:11434");

        String mockJsonResponse = "{\"response\": \"delegated\"}";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new org.springframework.http.ResponseEntity<>(mockJsonResponse, org.springframework.http.HttpStatus.OK));

        String result = ollamaClient.generateResponse("test");
        assertEquals("delegated", result);
    }

    @Test
    @DisplayName("generateResponse should throw on null response body")
    void generateResponseShouldThrowOnNullBody() {
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "restTemplate", restTemplate);
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "baseUrl", "http://localhost:11434");

        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new org.springframework.http.ResponseEntity<>(null, org.springframework.http.HttpStatus.OK));

        assertThrows(RuntimeException.class, () -> ollamaClient.generateResponse("test"));
    }

    @Test
    @DisplayName("generateResponse should throw on blank response field")
    void generateResponseShouldThrowOnBlankResponseField() {
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "restTemplate", restTemplate);
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "baseUrl", "http://localhost:11434");
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "objectMapper", new com.fasterxml.jackson.databind.ObjectMapper());

        String mockResponse = "{\"response\": \"   \"}";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new org.springframework.http.ResponseEntity<>(mockResponse, org.springframework.http.HttpStatus.OK));

        assertThrows(RuntimeException.class, () -> ollamaClient.generateResponse("test"));
    }

    @Test
    @DisplayName("generateResponse should throw on invalid JSON")
    void generateResponseShouldThrowOnInvalidJson() {
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "restTemplate", restTemplate);
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "baseUrl", "http://localhost:11434");
        org.springframework.test.util.ReflectionTestUtils.setField(ollamaClient, "objectMapper", new com.fasterxml.jackson.databind.ObjectMapper());

        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new org.springframework.http.ResponseEntity<>("not json at all", org.springframework.http.HttpStatus.OK));

        assertThrows(RuntimeException.class, () -> ollamaClient.generateResponse("test"));
    }
}

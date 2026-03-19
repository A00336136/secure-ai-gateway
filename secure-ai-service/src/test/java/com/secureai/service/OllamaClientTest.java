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
}

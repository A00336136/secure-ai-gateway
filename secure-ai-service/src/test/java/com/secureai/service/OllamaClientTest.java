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
        ollamaClient = new OllamaClient(restTemplate);
    }

    @Test
    @DisplayName("generateResponse should return text from Ollama API")
    void generateResponseShouldReturnText() {
        String expectedResponse = "Ollama's answer";
        java.util.Map<String, Object> mockBody = java.util.Map.of("response", expectedResponse);
        
        when(restTemplate.postForObject(anyString(), any(), eq(java.util.Map.class)))
                .thenReturn(mockBody);

        String result = ollamaClient.generateResponse("test prompt");

        assertEquals(expectedResponse, result);
        verify(restTemplate).postForObject(contains("/api/generate"), any(), eq(java.util.Map.class));
    }

    @Test
    @DisplayName("isHealthy should return true if API responds")
    void isHealthyShouldReturnTrue() {
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("OK");

        assertTrue(ollamaClient.isHealthy());
        verify(restTemplate).getForObject(contains("/api/tags"), eq(String.class));
    }
}

package com.secureai.guardrails;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PresidioClient Unit Tests")
class PresidioClientTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private PresidioClient client;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(client, "baseUrl", "http://localhost:5002");
        ReflectionTestUtils.setField(client, "enabled", true);
        ReflectionTestUtils.setField(client, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(client, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(client, "language", "en");
        ReflectionTestUtils.setField(client, "minScore", 0.6);
    }

    @Test
    @DisplayName("evaluate should pass when Presidio returns empty list")
    void evaluateShouldPassWhenNoPii() {
        String mockResponse = "[]";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        StepVerifier.create(client.evaluate("clean text"))
                .assertNext(result -> {
                    assertFalse(result.blocked());
                    assertEquals("Presidio", result.layerName());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("evaluate should block when Presidio detects PII")
    void evaluateShouldBlockWhenPiiDetected() {
        // Presidio's actual entity type for email addresses is EMAIL_ADDRESS, not EMAIL
        String mockResponse = "[{\"entity_type\": \"EMAIL_ADDRESS\", \"score\": 0.95, \"start\": 0, \"end\": 24}]";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        StepVerifier.create(client.evaluate("my email is test@test.com"))
                .assertNext(result -> {
                    assertTrue(result.blocked());
                    assertTrue(result.category().contains("EMAIL_ADDRESS"));
                    assertEquals(0.95, result.confidence());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("evaluate should block when service unreachable (fail-closed)")
    void evaluateShouldBlockWhenUnreachable() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new RestClientException("Connection error"));

        StepVerifier.create(client.evaluate("text"))
                .assertNext(result -> {
                    assertTrue(result.blocked());
                    assertEquals("service_unavailable", result.category());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("evaluate should block on parse error (fail-closed)")
    void evaluateShouldBlockOnParseError() {
        String invalidJson = "{ bad json }";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(invalidJson, HttpStatus.OK));

        StepVerifier.create(client.evaluate("text"))
                .assertNext(result -> {
                    assertTrue(result.blocked());
                    assertEquals("parse_error", result.category());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("evaluate should pass when disabled")
    void evaluateShouldPassWhenDisabled() {
        ReflectionTestUtils.setField(client, "enabled", false);

        StepVerifier.create(client.evaluate("text"))
                .assertNext(result -> assertFalse(result.blocked()))
                .verifyComplete();
    }

    @Test
    @DisplayName("isHealthy should return true if health endpoint is 200")
    void isHealthyShouldReturnTrue() {
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("OK", HttpStatus.OK));

        assertTrue(client.isHealthy());
    }

    @Test
    @DisplayName("isHealthy should return false on error")
    void isHealthyShouldReturnFalseOnError() {
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenThrow(new RestClientException("Down"));

        assertFalse(client.isHealthy());
    }
}

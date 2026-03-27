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
@DisplayName("LlamaGuardClient Unit Tests")
class LlamaGuardClientTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private LlamaGuardClient client;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(client, "ollamaBaseUrl", "http://localhost:11434");
        ReflectionTestUtils.setField(client, "llamaguardModel", "llamaguard3:8b");
        ReflectionTestUtils.setField(client, "enabled", true);
        ReflectionTestUtils.setField(client, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(client, "objectMapper", new ObjectMapper());
    }

    @Test
    @DisplayName("evaluate should pass when LlamaGuard returns 'safe'")
    void evaluateShouldPassWhenSafe() {
        String mockResponse = "{\"response\": \"safe\"}";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        StepVerifier.create(client.evaluate("hello"))
                .assertNext(result -> {
                    assertFalse(result.blocked());
                    assertEquals("LlamaGuard", result.layerName());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("evaluate should block when LlamaGuard returns 'unsafe'")
    void evaluateShouldBlockWhenUnsafe() {
        String mockResponse = "{\"response\": \"unsafe\\nS1\"}";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        StepVerifier.create(client.evaluate("harmful prompt"))
                .assertNext(result -> {
                    assertTrue(result.blocked());
                    assertEquals("S1", result.category());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("evaluate should block when service is unreachable (fail-closed)")
    void evaluateShouldBlockWhenUnreachable() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new RestClientException("Offline"));

        StepVerifier.create(client.evaluate("prompt"))
                .assertNext(result -> {
                    assertTrue(result.blocked());
                    assertEquals("service_unavailable", result.category());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("evaluate should block on parsing error (fail-closed)")
    void evaluateShouldBlockOnParseError() {
        String invalidJson = "invalid-json";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(invalidJson, HttpStatus.OK));

        StepVerifier.create(client.evaluate("prompt"))
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

        StepVerifier.create(client.evaluate("prompt"))
                .assertNext(result -> assertFalse(result.blocked()))
                .verifyComplete();
    }

    @Test
    @DisplayName("evaluate should pass when response is non-2xx")
    void evaluateShouldPassWhenNon2xx() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR));

        StepVerifier.create(client.evaluate("prompt"))
                .assertNext(result -> assertFalse(result.blocked()))
                .verifyComplete();
    }

    @Test
    @DisplayName("evaluate should pass when response body is null")
    void evaluateShouldPassWhenBodyNull() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        StepVerifier.create(client.evaluate("prompt"))
                .assertNext(result -> assertFalse(result.blocked()))
                .verifyComplete();
    }

    @Test
    @DisplayName("evaluate should block unsafe without newline as unknown category")
    void evaluateShouldBlockUnsafeWithoutNewline() {
        String mockResponse = "{\"response\": \"unsafe\"}";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        StepVerifier.create(client.evaluate("harmful"))
                .assertNext(result -> {
                    assertTrue(result.blocked());
                    assertEquals("unknown", result.category());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("evaluate should block when response field is null (empty string treated as unsafe)")
    void evaluateShouldBlockWhenResponseFieldNull() {
        String mockResponse = "{\"response\": null}";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        StepVerifier.create(client.evaluate("prompt"))
                .assertNext(result -> assertTrue(result.blocked()))
                .verifyComplete();
    }
}

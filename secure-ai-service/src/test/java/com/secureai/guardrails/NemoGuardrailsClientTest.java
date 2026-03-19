package com.secureai.guardrails;

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
@DisplayName("NemoGuardrailsClient Unit Tests")
class NemoGuardrailsClientTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private NemoGuardrailsClient client;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(client, "baseUrl", "http://localhost:8001");
        ReflectionTestUtils.setField(client, "enabled", true);
        ReflectionTestUtils.setField(client, "restTemplate", restTemplate);
    }

    @Test
    @DisplayName("evaluate should pass when API returns 2xx")
    void evaluateShouldPassWhenApiReturns2xx() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("OK", HttpStatus.OK));

        StepVerifier.create(client.evaluate("test prompt"))
                .assertNext(result -> {
                    assertFalse(result.blocked());
                    assertEquals("NeMo", result.layerName());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("evaluate should block when API returns 422")
    void evaluateShouldBlockWhenApiReturns422() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("Blocked", HttpStatus.UNPROCESSABLE_ENTITY));

        StepVerifier.create(client.evaluate("bad prompt"))
                .assertNext(result -> {
                    assertTrue(result.blocked());
                    assertEquals("colang_policy_violation", result.category());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("evaluate should pass when API returns unexpected status")
    void evaluateShouldPassWhenUnexpectedStatus() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("Error", HttpStatus.INTERNAL_SERVER_ERROR));

        StepVerifier.create(client.evaluate("prompt"))
                .assertNext(result -> assertFalse(result.blocked()))
                .verifyComplete();
    }

    @Test
    @DisplayName("evaluate should block when service is unreachable (fail-closed)")
    void evaluateShouldBlockWhenServiceUnreachable() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new RestClientException("Connection refused"));

        StepVerifier.create(client.evaluate("prompt"))
                .assertNext(result -> {
                    assertTrue(result.blocked());
                    assertEquals("service_unavailable", result.category());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("evaluate should return PASS instantly when disabled")
    void evaluateShouldPassWhenDisabled() {
        ReflectionTestUtils.setField(client, "enabled", false);

        StepVerifier.create(client.evaluate("prompt"))
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

package com.secureai.guardrails;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    // ─── Missing branch coverage: content-refusal detection in 2xx response ──

    @Test
    @DisplayName("evaluate should BLOCK when 2xx response body contains 'cannot comply' refusal text")
    void evaluateShouldBlockOnCannotComplyRefusal() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("I cannot comply with that request.", HttpStatus.OK));

        StepVerifier.create(client.evaluate("jailbreak attempt"))
                .assertNext(result -> {
                    assertTrue(result.blocked());
                    assertEquals("colang_policy_violation", result.category());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("evaluate should BLOCK when 2xx response body contains 'harmful' refusal text")
    void evaluateShouldBlockOnHarmfulRefusal() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("That request is harmful and cannot be processed.", HttpStatus.OK));

        StepVerifier.create(client.evaluate("harmful prompt"))
                .assertNext(result -> assertTrue(result.blocked()))
                .verifyComplete();
    }

    @Test
    @DisplayName("evaluate should BLOCK when 2xx response body contains 'been blocked' indicator")
    void evaluateShouldBlockOnBeenBlockedIndicator() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("Your request has been blocked for audit purposes.", HttpStatus.OK));

        StepVerifier.create(client.evaluate("suspicious prompt"))
                .assertNext(result -> assertTrue(result.blocked()))
                .verifyComplete();
    }

    @Test
    @DisplayName("evaluate should PASS when 2xx response body is null (null body branch)")
    void evaluateShouldPassWhenBodyIsNull() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        StepVerifier.create(client.evaluate("normal prompt"))
                .assertNext(result -> assertFalse(result.blocked()))
                .verifyComplete();
    }

    @Test
    @DisplayName("evaluate should BLOCK when 2xx response body contains 'security guardrails'")
    void evaluateShouldBlockOnSecurityGuardrailsText() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("This request violates security guardrails policy.", HttpStatus.OK));

        StepVerifier.create(client.evaluate("policy violation attempt"))
                .assertNext(result -> assertTrue(result.blocked()))
                .verifyComplete();
    }

    // ─── Branch coverage: all 18 OR-conditions in the refusal-detection if-block ─

    /**
     * Each string is crafted so it ONLY matches the target keyword (not any earlier one in
     * the chain), forcing each individual || branch to be the short-circuiting "true" case.
     * This covers the 14 remaining OR-condition branches not exercised by the tests above.
     */
    @ParameterizedTest(name = "evaluate should BLOCK when response body contains: \"{0}\"")
    @ValueSource(strings = {
            "I cannot provide that information.",          // "cannot provide"
            "I cannot assist with this request.",          // "cannot assist"
            "I am not able to respond to this.",           // "not able to"
            "I'm sorry, I can't do that.",                 // "i'm sorry"
            "I cannot help you with that request.",        // "cannot help"
            "This request involves illegal activity.",     // "illegal"
            "Your message has been flagged for review.",   // "flagged"
            "This interaction has been logged.",           // "been logged"
            "This violates content safety guidelines.",    // "content safety"
            "This was recorded for audit purposes only.",  // "audit purposes"
            "That action is not allowed here.",            // "not allowed"
            "We refuse to process this request.",          // "refuse"
            "This operation is not permitted.",            // "not permitted"
            "This constitutes a policy violation."         // "policy violation"
    })
    @DisplayName("evaluate should BLOCK when 2xx body contains untested refusal keyword")
    void evaluateShouldBlockOnEachRefusalKeyword(String refusalText) {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(refusalText, HttpStatus.OK));

        StepVerifier.create(client.evaluate("test prompt"))
                .assertNext(result -> assertTrue(result.blocked(),
                        "Expected block for refusal text: " + refusalText))
                .verifyComplete();
    }

    @Test
    @DisplayName("sanitizeLog null branch: evaluate should BLOCK and log when exception has null message")
    void evaluateShouldHandleNullExceptionMessage() {
        // RestClientException with null message → sanitizeLog(null) → "(null)"
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new RestClientException(null) {});

        StepVerifier.create(client.evaluate("prompt"))
                .assertNext(result -> {
                    assertTrue(result.blocked());
                    assertEquals("service_unavailable", result.category());
                })
                .verifyComplete();
    }
}

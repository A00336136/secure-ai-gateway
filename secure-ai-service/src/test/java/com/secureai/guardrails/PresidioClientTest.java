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

    // ─── Missing branch coverage ──────────────────────────────────────────────

    @Test
    @DisplayName("evaluate should PASS and log ignored types when only non-sensitive entities (PERSON, LOCATION) detected")
    void evaluateShouldPassAndIgnoreNonSensitiveEntities() {
        // PERSON and LOCATION are NOT in the SENSITIVE_TYPES set → should be ignored
        String mockResponse = "[{\"entity_type\": \"PERSON\", \"score\": 0.85, \"start\": 0, \"end\": 10},"
                + "{\"entity_type\": \"LOCATION\", \"score\": 0.9, \"start\": 15, \"end\": 25}]";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        StepVerifier.create(client.evaluate("Hello John in London"))
                .assertNext(result -> {
                    assertFalse(result.blocked(), "Non-sensitive entities (PERSON, LOCATION) should not block");
                    assertEquals("Presidio", result.layerName());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("evaluate should PASS when Presidio returns non-2xx response (fallback pass branch)")
    void evaluateShouldPassOnNon2xxResponse() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.SERVICE_UNAVAILABLE));

        StepVerifier.create(client.evaluate("some text"))
                .assertNext(result -> assertFalse(result.blocked(), "Non-2xx response should fall back to PASS"))
                .verifyComplete();
    }

    @Test
    @DisplayName("evaluate should BLOCK on mixed sensitive and non-sensitive entities")
    void evaluateShouldBlockWhenMixedEntitiesIncludeSensitive() {
        // Contains both PERSON (non-sensitive) and CREDIT_CARD (sensitive) → should block
        String mockResponse = "[{\"entity_type\": \"PERSON\", \"score\": 0.8, \"start\": 0, \"end\": 5},"
                + "{\"entity_type\": \"CREDIT_CARD\", \"score\": 0.99, \"start\": 10, \"end\": 26}]";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        StepVerifier.create(client.evaluate("John 4532015112830366"))
                .assertNext(result -> {
                    assertTrue(result.blocked());
                    assertTrue(result.category().contains("CREDIT_CARD"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("evaluate should BLOCK when Presidio detects US_SSN (sensitive type)")
    void evaluateShouldBlockOnSsnDetection() {
        String mockResponse = "[{\"entity_type\": \"US_SSN\", \"score\": 0.95, \"start\": 0, \"end\": 11}]";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        StepVerifier.create(client.evaluate("SSN: 123-45-6789"))
                .assertNext(result -> {
                    assertTrue(result.blocked());
                    assertTrue(result.category().contains("US_SSN"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("evaluate should PASS when 2xx response body is null (null body with 2xx branch)")
    void evaluateShouldPassWhen2xxBodyIsNull() {
        // is2xxSuccessful()=true but getBody()=null → compound && is false → fall through to pass
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        StepVerifier.create(client.evaluate("some text"))
                .assertNext(result -> assertFalse(result.blocked(), "2xx with null body should fall through to PASS"))
                .verifyComplete();
    }

    @Test
    @DisplayName("sanitizeLog null branch: evaluate should BLOCK and handle null RestClientException message")
    void evaluateShouldHandleNullExceptionMessage() {
        // RestClientException with null message → sanitizeLog(null) → "(null)"
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new RestClientException(null) {});

        StepVerifier.create(client.evaluate("text"))
                .assertNext(result -> {
                    assertTrue(result.blocked());
                    assertEquals("service_unavailable", result.category());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("PresidioEntity getStart() and getEnd() are accessible (private inner class getters)")
    void presidioEntityGetStartAndGetEndAreCallable() throws Exception {
        // PresidioEntity is a private static inner class — use reflection with setAccessible to access it
        Class<?> entityClass = Class.forName("com.secureai.guardrails.PresidioClient$PresidioEntity");
        var constructor = entityClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object entity = constructor.newInstance();

        java.lang.reflect.Method setStart = entityClass.getDeclaredMethod("setStart", int.class);
        java.lang.reflect.Method setEnd   = entityClass.getDeclaredMethod("setEnd",   int.class);
        java.lang.reflect.Method getStart = entityClass.getDeclaredMethod("getStart");
        java.lang.reflect.Method getEnd   = entityClass.getDeclaredMethod("getEnd");

        setStart.setAccessible(true);
        setEnd.setAccessible(true);
        getStart.setAccessible(true);
        getEnd.setAccessible(true);

        setStart.invoke(entity, 5);
        setEnd.invoke(entity, 20);

        assertEquals(5,  getStart.invoke(entity), "getStart() should return the set value");
        assertEquals(20, getEnd.invoke(entity),   "getEnd() should return the set value");
    }
}

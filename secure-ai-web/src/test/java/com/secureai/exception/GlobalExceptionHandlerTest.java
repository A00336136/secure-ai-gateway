package com.secureai.exception;

import com.secureai.guardrails.GuardrailsBlockedException;
import com.secureai.model.ErrorResponse;
import com.secureai.service.AuthService.AuthException;
import com.secureai.service.OllamaClient.OllamaException;
import jakarta.servlet.http.HttpServletRequest;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/test");
    }

    @Test
    @DisplayName("handleAuth should return 401 with error details")
    void handleAuthShouldReturn401() {
        AuthException ex = new AuthException("Invalid credentials");

        ResponseEntity<ErrorResponse> response = handler.handleAuth(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(401);
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid credentials");
    }

    @Test
    @DisplayName("handleGuardrailsBlocked with remainingTokens should include rate limit header")
    void handleGuardrailsBlockedWithRemainingTokens() {
        GuardrailsBlockedException ex = new GuardrailsBlockedException("NeMo:jailbreak", 95L);

        ResponseEntity<ErrorResponse> response = handler.handleGuardrailsBlocked(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getHeaders().getFirst("X-Guardrails-Status")).isEqualTo("BLOCKED");
        assertThat(response.getHeaders().getFirst("X-Guardrails-Blocked-By")).isEqualTo("NeMo:jailbreak");
        assertThat(response.getHeaders().getFirst("X-Rate-Limit-Remaining")).isEqualTo("95");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(422);
    }

    @Test
    @DisplayName("handleGuardrailsBlocked without remainingTokens should not include rate limit header")
    void handleGuardrailsBlockedWithoutRemainingTokens() {
        GuardrailsBlockedException ex = new GuardrailsBlockedException("LlamaGuard:S1");

        ResponseEntity<ErrorResponse> response = handler.handleGuardrailsBlocked(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getHeaders().getFirst("X-Guardrails-Status")).isEqualTo("BLOCKED");
        assertThat(response.getHeaders().getFirst("X-Rate-Limit-Remaining")).isNull();
    }

    @Test
    @DisplayName("handleOllama should return 503")
    void handleOllamaShouldReturn503() {
        OllamaException ex = new OllamaException("Connection refused");

        ResponseEntity<ErrorResponse> response = handler.handleOllama(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(503);
    }

    @Test
    @DisplayName("handleValidation should return 400 with field errors")
    void handleValidationShouldReturn400() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("request", "prompt", "must not be blank");
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("prompt: must not be blank");
    }

    @Test
    @DisplayName("handleConstraint should return 400")
    void handleConstraintShouldReturn400() {
        ConstraintViolationException ex = new ConstraintViolationException("Validation failed", Set.of());

        ResponseEntity<ErrorResponse> response = handler.handleConstraint(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("handleAccessDenied should return 403")
    void handleAccessDeniedShouldReturn403() {
        AccessDeniedException ex = new AccessDeniedException("Forbidden");

        ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(403);
        assertThat(response.getBody().getMessage()).isEqualTo("Insufficient privileges");
    }

    @Test
    @DisplayName("handleGeneric should return 500")
    void handleGenericShouldReturn500() {
        RuntimeException ex = new RuntimeException("Something broke");

        ResponseEntity<ErrorResponse> response = handler.handleGeneric(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(500);
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
    }

    @Test
    @DisplayName("handleAuth should sanitize CRLF in URI and message")
    void handleAuthShouldSanitizeCrlf() {
        when(request.getRequestURI()).thenReturn("/api/test\r\nInjected");
        AuthException ex = new AuthException("Bad\r\ninjection");

        ResponseEntity<ErrorResponse> response = handler.handleAuth(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("handleGeneric should handle null message in exception")
    void handleGenericShouldHandleNullMessage() {
        RuntimeException ex = new RuntimeException((String) null);

        ResponseEntity<ErrorResponse> response = handler.handleGeneric(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}

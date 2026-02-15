package com.secureai.controller;

import com.secureai.dto.AskRequest;
import com.secureai.dto.AskResponse;
import com.secureai.exception.SecureAiException;
import com.secureai.service.OllamaService;
import com.secureai.service.PiiRedactionService;
import com.secureai.service.RateLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Controller for AI query endpoints.
 * Implements rate limiting, PII redaction, and secure AI access.
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "AI Gateway", description = "AI query endpoints with PII protection")
@SecurityRequirement(name = "Bearer Authentication")
public class AskController {

    private final OllamaService ollamaService;
    private final PiiRedactionService piiRedactionService;
    private final RateLimitService rateLimitService;

    /**
     * Process an AI query with PII redaction.
     *
     * @param request        the AI query request
     * @param authentication the authentication object containing user details
     * @return ResponseEntity with AI response
     */
    @PostMapping("/ask")
    @Operation(summary = "Query AI model", description = "Send a prompt to the AI model and receive a response with PII redaction")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully processed query"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<AskResponse> ask(
            @Valid @RequestBody AskRequest request,
            Authentication authentication) {

        String username = authentication.getName();
        String requestId = UUID.randomUUID().toString();
        
        log.info("Processing AI query - RequestID: {}, User: {}, PromptLength: {}", 
                requestId, username, request.getPrompt().length());

        try {
            // Check rate limit
            if (!rateLimitService.isAllowed(username)) {
                log.warn("Rate limit exceeded for user: {}", username);
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(AskResponse.builder()
                                .response("Rate limit exceeded. Please try again later.")
                                .redacted(false)
                                .requestId(requestId)
                                .timestamp(Instant.now())
                                .build());
            }

            // Get AI response
            String aiResponse = ollamaService.generateResponse(request.getPrompt());

            // Check for and redact PII
            boolean hasPii = piiRedactionService.containsPii(aiResponse);
            String finalResponse = hasPii ? piiRedactionService.redact(aiResponse) : aiResponse;

            if (hasPii) {
                log.info("PII detected and redacted - RequestID: {}, Types: {}", 
                        requestId, piiRedactionService.detectPiiTypes(aiResponse));
            }

            AskResponse response = AskResponse.builder()
                    .response(finalResponse)
                    .redacted(hasPii)
                    .requestId(requestId)
                    .timestamp(Instant.now())
                    .build();

            log.info("Query processed successfully - RequestID: {}, Redacted: {}", requestId, hasPii);
            return ResponseEntity.ok(response);

        } catch (SecureAiException e) {
            log.error("Error processing query - RequestID: {}, Error: {}", requestId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error processing query - RequestID: {}", requestId, e);
            throw new SecureAiException("Failed to process AI query", e);
        }
    }

    /**
     * Health check endpoint for the AI service.
     *
     * @return ResponseEntity with service status
     */
    @GetMapping("/health")
    @Operation(summary = "Check AI service health", description = "Verify that the AI service is available")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Service is healthy"),
            @ApiResponse(responseCode = "503", description = "Service unavailable")
    })
    public ResponseEntity<String> health() {
        boolean isAvailable = ollamaService.isAvailable();
        
        if (isAvailable) {
            return ResponseEntity.ok("AI service is available");
        } else {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("AI service is unavailable");
        }
    }

    /**
     * Get remaining rate limit tokens for the current user.
     *
     * @param authentication the authentication object
     * @return ResponseEntity with remaining tokens
     */
    @GetMapping("/rate-limit")
    @Operation(summary = "Check rate limit", description = "Get remaining API calls for the current user")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved rate limit info")
    public ResponseEntity<Long> getRateLimit(Authentication authentication) {
        String username = authentication.getName();
        long remaining = rateLimitService.getRemainingTokens(username);
        
        log.debug("Rate limit check for user: {}, Remaining: {}", username, remaining);
        return ResponseEntity.ok(remaining);
    }
}

package com.secureai.controller;

import com.secureai.agent.ReActAgentService;
import com.secureai.model.AskRequest;
import com.secureai.model.AskResponse;
import com.secureai.pii.PiiRedactionService;
import com.secureai.service.AuditLogService;
import com.secureai.service.OllamaClient;
import com.secureai.service.RateLimiterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * Ask Controller — Main AI Gateway Endpoint
 *
 * Pipeline per request:
 *  ① JWT auth (enforced by security filter, not this controller)
 *  ② Rate limit check (Bucket4j — 100 req/hr per user)
 *  ③ Route to OllamaClient or ReActAgent
 *  ④ PII redaction on response
 *  ⑤ Async audit log to PostgreSQL
 *  ⑥ Return response with rate-limit headers
 */
@RestController
@RequestMapping("/api")
@Tag(name = "AI Gateway", description = "Secure AI inference endpoints")
public class AskController {

    private static final Logger log = LoggerFactory.getLogger(AskController.class);

    @Autowired private OllamaClient ollamaClient;
    @Autowired private PiiRedactionService piiRedactionService;
    @Autowired private RateLimiterService rateLimiterService;
    @Autowired private ReActAgentService reActAgentService;
    @Autowired private AuditLogService auditLogService;

    @PostMapping("/ask")
    @Operation(
        summary = "Send a prompt to the AI gateway",
        description = "Authenticated, rate-limited, PII-redacted AI inference",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<AskResponse> ask(
            @Valid @RequestBody AskRequest request,
            Principal principal,
            HttpServletRequest httpRequest) {

        String username = principal.getName();
        long startTime = System.currentTimeMillis();

        // ② Rate Limiting
        if (!rateLimiterService.tryConsume(username)) {
            long remaining = rateLimiterService.getRemainingTokens(username);
            log.warn("Rate limit exceeded for user '{}'", sanitizeLog(username));

            auditLogService.logRequest(username, request.getPrompt(), null,
                    ollamaClient.getModel(), false, true, null,
                    429, 0L, httpRequest.getRemoteAddr());

            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("X-Rate-Limit-Remaining", String.valueOf(remaining))
                    .header("Retry-After", "3600")
                    .header("X-Rate-Limit-Capacity", String.valueOf(rateLimiterService.getCapacity()))
                    .build();
        }

        String rawResponse;
        int reactSteps = 0;

        // ③ Route: ReAct agent or direct inference
        if (request.isUseReActAgent()) {
            log.info("ReAct agent invoked for user '{}'", sanitizeLog(username));
            ReActAgentService.AgentResult result = reActAgentService.execute(request.getPrompt());
            rawResponse = result.answer;
            reactSteps = result.totalSteps;
        } else {
            rawResponse = ollamaClient.generateResponse(request.getPrompt());
        }

        // ④ PII Redaction
        boolean piiDetected = piiRedactionService.containsPii(rawResponse);
        String finalResponse = piiDetected ? piiRedactionService.redact(rawResponse) : rawResponse;

        long durationMs = System.currentTimeMillis() - startTime;

        // ⑤ Async Audit Log
        auditLogService.logRequest(
                username, request.getPrompt(), finalResponse,
                ollamaClient.getModel(), piiDetected, false,
                reactSteps > 0 ? reactSteps : null,
                200, durationMs, httpRequest.getRemoteAddr()
        );

        log.info("Request processed for '{}': pii={}, steps={}, ms={}",
                sanitizeLog(username), piiDetected, reactSteps, durationMs);

        long remaining = rateLimiterService.getRemainingTokens(username);

        // ⑥ Return response
        AskResponse response = new AskResponse(
                finalResponse, piiDetected, piiDetected, reactSteps, durationMs,
                ollamaClient.getModel()
        );

        return ResponseEntity.ok()
                .header("X-Rate-Limit-Remaining", String.valueOf(remaining))
                .header("X-Rate-Limit-Capacity", String.valueOf(rateLimiterService.getCapacity()))
                .header("X-PII-Redacted", String.valueOf(piiDetected))
                .header("X-Duration-Ms", String.valueOf(durationMs))
                .body(response);
    }

    @GetMapping("/status")
    @Operation(summary = "Check AI model connectivity", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Object> status(Principal principal) {
        boolean ollamaHealthy = ollamaClient.isHealthy();
        return ResponseEntity.ok(java.util.Map.of(
            "user", principal.getName(),
            "ollamaHealthy", ollamaHealthy,
            "model", ollamaClient.getModel(),
            "rateLimitRemaining", rateLimiterService.getRemainingTokens(principal.getName())
        ));
    }

    /** Strips CR and LF to prevent CRLF injection in log messages. */
    private static String sanitizeLog(String value) {
        if (value == null) return "(null)";
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }
}

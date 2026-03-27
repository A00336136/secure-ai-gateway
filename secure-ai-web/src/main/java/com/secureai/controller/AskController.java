package com.secureai.controller;

import com.secureai.agent.ReActAgentService;
import com.secureai.guardrails.GuardrailsBlockedException;
import com.secureai.guardrails.GuardrailsOrchestrator;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * Ask Controller — Main AI Gateway Endpoint
 *
 * Pipeline per request:
 *  ① JWT auth (enforced by security filter, not this controller)
 *  ② Rate limit check (Bucket4j — 100 req/hr per user)
 *  ③ 3-Layer Guardrails (NeMo + LlamaGuard + Presidio in parallel via Mono.zip())
 *  ④ Route to OllamaClient or ReActAgent
 *  ⑤ PII redaction on response
 *  ⑥ Async audit log to PostgreSQL
 *  ⑦ Return response with rate-limit headers
 */
@RestController
@RequestMapping("/api")
@Tag(name = "AI Gateway", description = "Secure AI inference endpoints")
public class AskController {

    private static final Logger log = LoggerFactory.getLogger(AskController.class);

    private final OllamaClient ollamaClient;
    private final PiiRedactionService piiRedactionService;
    private final RateLimiterService rateLimiterService;
    private final ReActAgentService reActAgentService;
    private final AuditLogService auditLogService;
    private final GuardrailsOrchestrator guardrailsOrchestrator;

    public AskController(OllamaClient ollamaClient, PiiRedactionService piiRedactionService,
                         RateLimiterService rateLimiterService, ReActAgentService reActAgentService,
                         AuditLogService auditLogService, GuardrailsOrchestrator guardrailsOrchestrator) {
        this.ollamaClient = ollamaClient;
        this.piiRedactionService = piiRedactionService;
        this.rateLimiterService = rateLimiterService;
        this.reActAgentService = reActAgentService;
        this.auditLogService = auditLogService;
        this.guardrailsOrchestrator = guardrailsOrchestrator;
    }

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
            log.warn("Rate limit exceeded for user '{}'", username);

            auditLogService.logRequest(new AuditLogService.AuditLogEntry(
                    username, request.getPrompt(), null,
                    ollamaClient.getModel(), false, true, null,
                    429, 0L, httpRequest.getRemoteAddr()));

            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("X-Rate-Limit-Remaining", String.valueOf(remaining))
                    .header("Retry-After", "3600")
                    .header("X-Rate-Limit-Capacity", String.valueOf(rateLimiterService.getCapacity()))
                    .build();
        }

        // ③ 3-Layer Guardrails (NeMo + LlamaGuard + Presidio — parallel Mono.zip())
        var guardrailsResult = guardrailsOrchestrator.evaluate(request.getPrompt());
        if (guardrailsResult.blocked()) {
            log.warn("Guardrails BLOCKED for user '{}': {}", username, guardrailsResult.blockedBy());

            auditLogService.logRequest(new AuditLogService.AuditLogEntry(
                    username, request.getPrompt(), null,
                    ollamaClient.getModel(), false, false, null,
                    422, guardrailsResult.totalLatencyMs(), httpRequest.getRemoteAddr()));

            long remaining = rateLimiterService.getRemainingTokens(username);
            throw new GuardrailsBlockedException(guardrailsResult.blockedBy(), remaining);
        }

        String rawResponse;
        int reactSteps = 0;

        // ④ Route: ReAct agent or direct inference
        if (request.isUseReActAgent()) {
            log.info("ReAct agent invoked for user '{}'", username);
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
        auditLogService.logRequest(new AuditLogService.AuditLogEntry(
                username, request.getPrompt(), finalResponse,
                ollamaClient.getModel(), piiDetected, false,
                reactSteps > 0 ? reactSteps : null,
                200, durationMs, httpRequest.getRemoteAddr()
        ));

        log.info("Request processed for '{}': pii={}, steps={}, ms={}",
                username, piiDetected, reactSteps, durationMs);

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

}

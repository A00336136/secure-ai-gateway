package com.secureai.controller;

import com.secureai.agent.ReActAgentService;
import com.secureai.guardrails.GuardrailsBlockedException;
import com.secureai.guardrails.GuardrailsOrchestrator;
import com.secureai.model.AskRequest;
import com.secureai.model.AskResponse;
import com.secureai.pii.PiiRedactionService;
import com.secureai.service.AuditLogService;
import com.secureai.service.GroundednessCheckerService;
import com.secureai.service.OllamaClient;
import com.secureai.service.RateLimiterService;
import com.secureai.service.TokenCounterService;
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
 *  ② Rate limit check (Bucket4j — 100 req/hr per user; Redis-backed in prod)
 *  ③ 3-Layer Guardrails (NeMo + LlamaGuard + Presidio in parallel via Mono.zip())
 *  ④ Route to OllamaClient or ReActAgent
 *  ⑤ PII redaction on response
 *  ⑥ Groundedness check (NIST AI 600-1 / OWASP LLM09 — hallucination detection)
 *  ⑦ Token counting (OWASP LLM10 — unbounded consumption detection)
 *  ⑧ Async audit log to PostgreSQL (SOC 2 PI1 / HIPAA AU — immutable, append-only)
 *  ⑨ Return response with security headers
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
    private final GroundednessCheckerService groundednessCheckerService;
    private final TokenCounterService tokenCounterService;

    public AskController(OllamaClient ollamaClient, PiiRedactionService piiRedactionService,
                         RateLimiterService rateLimiterService, ReActAgentService reActAgentService,
                         AuditLogService auditLogService, GuardrailsOrchestrator guardrailsOrchestrator,
                         GroundednessCheckerService groundednessCheckerService,
                         TokenCounterService tokenCounterService) {
        this.ollamaClient = ollamaClient;
        this.piiRedactionService = piiRedactionService;
        this.rateLimiterService = rateLimiterService;
        this.reActAgentService = reActAgentService;
        this.auditLogService = auditLogService;
        this.guardrailsOrchestrator = guardrailsOrchestrator;
        this.groundednessCheckerService = groundednessCheckerService;
        this.tokenCounterService = tokenCounterService;
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
                    422, guardrailsResult.totalLatencyMs(), httpRequest.getRemoteAddr(),
                    guardrailsResult.blockedBy(), guardrailsResult.totalLatencyMs(),
                    computeRequestHash(request.getPrompt()),
                    null, false, null, null));

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

        // ⑤ PII Redaction
        boolean piiDetected = piiRedactionService.containsPii(rawResponse);
        String finalResponse = piiDetected ? piiRedactionService.redact(rawResponse) : rawResponse;

        // ⑥ Groundedness check — NIST AI 600-1 / OWASP LLM09 (hallucination detection)
        var groundedness = groundednessCheckerService.evaluate(request.getPrompt(), finalResponse);
        if (groundedness.flagged()) {
            log.warn("GROUNDEDNESS FLAG for user '{}': score={} verdict={}",
                    username, groundedness.score(), groundedness.verdict());
        }

        // ⑦ Token counting — OWASP LLM10 (unbounded consumption)
        var tokenCount = tokenCounterService.count(request.getPrompt(), finalResponse);
        if (tokenCount.excessiveUsage()) {
            log.warn("EXCESSIVE TOKEN USAGE for user '{}': total={} tokens", username, tokenCount.totalTokens());
        }

        long durationMs = System.currentTimeMillis() - startTime;

        // ⑧ Async Audit Log — immutable, append-only (SOC 2 PI1 / HIPAA AU)
        auditLogService.logRequest(new AuditLogService.AuditLogEntry(
                username, request.getPrompt(), finalResponse,
                ollamaClient.getModel(), piiDetected, false,
                reactSteps > 0 ? reactSteps : null,
                200, durationMs, httpRequest.getRemoteAddr(),
                null, guardrailsResult.totalLatencyMs(),
                computeRequestHash(request.getPrompt()),
                tokenCount.totalTokens(), tokenCount.excessiveUsage(),
                groundedness.score(), groundedness.verdict()
        ));

        log.info("Request processed for '{}': pii={}, tokens={}, groundedness={}, ms={}",
                username, piiDetected, tokenCount.totalTokens(), groundedness.verdict(), durationMs);

        long remaining = rateLimiterService.getRemainingTokens(username);

        // ⑨ Return response with full security headers
        AskResponse response = new AskResponse(
                finalResponse, piiDetected, piiDetected, reactSteps, durationMs,
                ollamaClient.getModel(), tokenCount.totalTokens(), groundedness.score(),
                groundedness.verdict()
        );

        return ResponseEntity.ok()
                .header("X-Rate-Limit-Remaining", String.valueOf(remaining))
                .header("X-Rate-Limit-Capacity", String.valueOf(rateLimiterService.getCapacity()))
                .header("X-PII-Redacted", String.valueOf(piiDetected))
                .header("X-Duration-Ms", String.valueOf(durationMs))
                .header("X-Tokens-Used", String.valueOf(tokenCount.totalTokens()))
                .header("X-Groundedness-Score", String.format("%.2f", groundedness.score()))
                .header("X-Groundedness-Verdict", groundedness.verdict())
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
            "rateLimitRemaining", rateLimiterService.getRemainingTokens(principal.getName()),
            "guardrailsHealthy", guardrailsOrchestrator.isHealthy(),
            "groundednessEnabled", true
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Compute SHA-256 hash of the prompt for tamper-evident audit records.
     * The hash allows forensic verification that a logged prompt was not altered.
     */
    private String computeRequestHash(String prompt) {
        if (prompt == null) return null;
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(prompt.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            log.warn("SHA-256 not available for request hash: {}", e.getMessage());
            return null;
        }
    }

}

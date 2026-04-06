package com.secureai.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Immutable Audit Log — SOC 2 PI1 / HIPAA AU / CIS Control 8
 *
 * Every AI gateway request is recorded as an append-only row.
 * All security-relevant columns are marked updatable=false to enforce
 * immutability at the JPA layer — records cannot be tampered with after
 * creation. This satisfies:
 *   - SOC 2 Type II PI1 (Processing Integrity): complete audit trail
 *   - HIPAA §164.312(b): audit controls for all ePHI access
 *   - CIS Control 8: Audit Log Management
 *   - NIST AI 600-1: Confabulation and Data Provenance tracking
 */
@Entity
@Table(name = "audit_logs")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100, updatable = false)
    private String username;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String prompt;

    @Column(columnDefinition = "TEXT")
    private String response;       // Always PII-redacted before persisting

    @Column(length = 100, updatable = false)
    private String model;

    @Column(name = "pii_detected", nullable = false, updatable = false)
    @Builder.Default
    private boolean piiDetected = false;

    @Column(name = "rate_limited", nullable = false, updatable = false)
    @Builder.Default
    private boolean rateLimited = false;

    @Column(name = "react_steps", updatable = false)
    private Integer reactSteps;

    @Column(name = "status_code", updatable = false)
    private Integer statusCode;

    @Column(name = "duration_ms", updatable = false)
    private Long durationMs;

    @Column(name = "ip_address", length = 50, updatable = false)
    private String ipAddress;

    // ── Security fields (SOC 2 / NIST AI 600-1) ──────────────────────────────

    /** Which guardrail layer(s) blocked the request (null = allowed). */
    @Column(name = "blocked_by", length = 200, updatable = false)
    private String blockedBy;

    /** Total guardrail evaluation latency in milliseconds. */
    @Column(name = "guardrail_latency_ms", updatable = false)
    private Long guardrailLatencyMs;

    /** SHA-256 hash of the original (pre-redaction) prompt for tamper detection. */
    @Column(name = "request_hash", length = 64, updatable = false)
    private String requestHash;

    // ── Token consumption (OWASP LLM10) ──────────────────────────────────────

    /** Estimated tokens consumed: prompt + response (for capacity planning). */
    @Column(name = "tokens_used", updatable = false)
    private Integer tokensUsed;

    /** True if token usage exceeded OWASP LLM10 safe thresholds. */
    @Column(name = "excessive_token_usage", nullable = false, updatable = false)
    @Builder.Default
    private boolean excessiveTokenUsage = false;

    // ── Groundedness (NIST AI 600-1 / OWASP LLM09) ───────────────────────────

    /** Groundedness score 0.0–1.0 (1.0 = fully grounded, <0.4 = likely hallucinated). */
    @Column(name = "groundedness_score")
    private Double groundednessScore;

    /** Groundedness verdict: GROUNDED, PARTIAL, UNGROUNDED, UNKNOWN, or SKIPPED. */
    @Column(name = "groundedness_verdict", length = 20)
    private String groundednessVerdict;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

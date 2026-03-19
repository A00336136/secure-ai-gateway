package com.secureai.service;

import com.secureai.model.AuditLog;
import com.secureai.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Audit Log Service
 *
 * Persists every AI request + PII-redacted response to PostgreSQL.
 * Logs are immutable append-only records for compliance and forensics.
 * All persistence is async to avoid blocking the request thread.
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Parameter object for {@link #logRequest(AuditLogEntry)} to reduce method parameter count.
     */
    public record AuditLogEntry(
            String username,
            String prompt,
            String redactedResponse,
            String model,
            boolean piiDetected,
            boolean rateLimited,
            Integer reactSteps,
            int statusCode,
            long durationMs,
            String ipAddress
    ) {}

    /**
     * Asynchronously persist an audit entry.
     * Does NOT block the HTTP response — fire and forget.
     */
    @Async
    public void logRequest(AuditLogEntry entry) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .username(entry.username())
                    .prompt(truncate(entry.prompt(), 4000))
                    .response(truncate(entry.redactedResponse(), 8000))
                    .model(entry.model())
                    .piiDetected(entry.piiDetected())
                    .rateLimited(entry.rateLimited())
                    .reactSteps(entry.reactSteps())
                    .statusCode(entry.statusCode())
                    .durationMs(entry.durationMs())
                    .ipAddress(entry.ipAddress())
                    .build();

            auditLogRepository.save(auditLog);
            if (log.isDebugEnabled()) {
                log.debug("Audit log saved for user '{}'", sanitizeLog(entry.username()));
            }
        } catch (Exception e) {
            if (log.isErrorEnabled()) {
                log.error("Failed to save audit log for user '{}': {}", sanitizeLog(entry.username()),
                        sanitizeLog(e.getMessage()), e);
            }
        }
    }

    public Page<AuditLog> getRecentLogs(int page, int size) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    public Page<AuditLog> getUserLogs(String username, int page, int size) {
        return auditLogRepository.findByUsernameOrderByCreatedAtDesc(username, PageRequest.of(page, size));
    }

    public List<AuditLog> getPiiAlerts() {
        return auditLogRepository.findByPiiDetectedTrueOrderByCreatedAtDesc();
    }

    public Map<String, Object> getDashboardStats() {
        LocalDateTime last24h = LocalDateTime.now().minusHours(24);
        LocalDateTime last1h  = LocalDateTime.now().minusHours(1);

        Double avgResponseTime = auditLogRepository.avgResponseTimeSince(last24h);

        return Map.of(
            "totalRequests",      auditLogRepository.count(),
            "requestsLast24h",    auditLogRepository.countRequestsSince(last24h),
            "requestsLastHour",   auditLogRepository.countRequestsSince(last1h),
            "piiDetections",      auditLogRepository.countByPiiDetectedTrue(),
            "rateLimitedCount",   auditLogRepository.countByRateLimitedTrue(),
            "avgResponseTimeMs",  avgResponseTime != null ? avgResponseTime : 0.0
        );
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...[truncated]";
    }

    /** Strips CR and LF to prevent CRLF injection in log messages. */
    private static String sanitizeLog(String value) {
        if (value == null) return "(null)";
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }
}

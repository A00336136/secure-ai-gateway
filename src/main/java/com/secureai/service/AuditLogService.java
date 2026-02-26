package com.secureai.service;

import com.secureai.model.AuditLog;
import com.secureai.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private AuditLogRepository auditLogRepository;

    /**
     * Asynchronously persist an audit entry.
     * Does NOT block the HTTP response — fire and forget.
     */
    @Async
    public void logRequest(String username, String prompt, String redactedResponse,
                           String model, boolean piiDetected, boolean rateLimited,
                           Integer reactSteps, int statusCode, long durationMs,
                           String ipAddress) {
        try {
            AuditLog entry = AuditLog.builder()
                    .username(username)
                    .prompt(truncate(prompt, 4000))
                    .response(truncate(redactedResponse, 8000))
                    .model(model)
                    .piiDetected(piiDetected)
                    .rateLimited(rateLimited)
                    .reactSteps(reactSteps)
                    .statusCode(statusCode)
                    .durationMs(durationMs)
                    .ipAddress(ipAddress)
                    .build();

            auditLogRepository.save(entry);
            log.debug("Audit log saved for user '{}'", sanitizeLog(username));
        } catch (Exception e) {
            log.error("Failed to save audit log for user '{}': {}", sanitizeLog(username),
                    sanitizeLog(e.getMessage()), e);
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
        return Map.of(
            "totalRequests",      auditLogRepository.count(),
            "requestsLast24h",    auditLogRepository.countRequestsSince(last24h),
            "requestsLastHour",   auditLogRepository.countRequestsSince(last1h),
            "piiDetections",      auditLogRepository.countByPiiDetectedTrue(),
            "rateLimitedCount",   auditLogRepository.countByRateLimitedTrue(),
            "avgResponseTimeMs",  auditLogRepository.avgResponseTimeSince(last24h)
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

package com.secureai.controller;

import com.secureai.model.AuditLog;
import com.secureai.service.AuditLogService;
import com.secureai.service.RateLimiterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@Tag(name = "Admin", description = "Admin-only audit + management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    @Autowired private AuditLogService auditLogService;
    @Autowired private RateLimiterService rateLimiterService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Dashboard statistics")
    public ResponseEntity<Map<String, Object>> dashboard() {
        return ResponseEntity.ok(auditLogService.getDashboardStats());
    }

    @GetMapping("/audit")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Paginated audit logs")
    public ResponseEntity<Page<AuditLog>> auditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(auditLogService.getRecentLogs(page, size));
    }

    @GetMapping("/audit/pii-alerts")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Requests where PII was detected")
    public ResponseEntity<List<AuditLog>> piiAlerts() {
        return ResponseEntity.ok(auditLogService.getPiiAlerts());
    }

    @DeleteMapping("/rate-limit/{username}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reset rate limit bucket for a user")
    public ResponseEntity<Map<String, String>> resetRateLimit(@PathVariable String username) {
        rateLimiterService.resetBucket(username);
        return ResponseEntity.ok(Map.of(
            "message", "Rate limit reset for user: " + username,
            "status", "success"
        ));
    }
}
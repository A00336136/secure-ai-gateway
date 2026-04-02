package com.secureai.service;

import com.secureai.model.AuditLog;
import com.secureai.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogService Unit Tests")
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogService(auditLogRepository);
    }

    @Test
    @DisplayName("logRequest should save an AuditLog entry with truncated fields")
    void logRequestShouldSaveAuditLog() {
        String longPrompt = "a".repeat(5000);
        String longResponse = "b".repeat(9000);

        auditLogService.logRequest(new AuditLogService.AuditLogEntry(
                "testuser", longPrompt, longResponse, "gpt-4", false, false, 0, 200, 100L, "127.0.0.1"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals("testuser", saved.getUsername());
        assertTrue(saved.getPrompt().length() <= 4015); // 4000 + "...[truncated]"
        assertTrue(saved.getPrompt().endsWith("...[truncated]"));
        assertTrue(saved.getResponse().length() <= 8015);
        assertTrue(saved.getResponse().endsWith("...[truncated]"));
        assertEquals("gpt-4", saved.getModel());
        assertEquals(200, saved.getStatusCode());
        assertEquals(100L, saved.getDurationMs());
    }

    @Test
    @DisplayName("getRecentLogs should call repository and return a page")
    void getRecentLogsShouldReturnPage() {
        Page<AuditLog> expectedPage = new PageImpl<>(List.of(new AuditLog()));
        when(auditLogRepository.findAllByOrderByCreatedAtDesc(any(PageRequest.class))).thenReturn(expectedPage);

        Page<AuditLog> result = auditLogService.getRecentLogs(0, 10);

        assertEquals(expectedPage, result);
        verify(auditLogRepository).findAllByOrderByCreatedAtDesc(PageRequest.of(0, 10));
    }

    @Test
    @DisplayName("getUserLogs should call repository and return a page")
    void getUserLogsShouldReturnPage() {
        Page<AuditLog> expectedPage = new PageImpl<>(List.of(new AuditLog()));
        when(auditLogRepository.findByUsernameOrderByCreatedAtDesc(eq("alice"), any(PageRequest.class))).thenReturn(expectedPage);

        Page<AuditLog> result = auditLogService.getUserLogs("alice", 0, 10);

        assertEquals(expectedPage, result);
        verify(auditLogRepository).findByUsernameOrderByCreatedAtDesc("alice", PageRequest.of(0, 10));
    }

    @Test
    @DisplayName("getPiiAlerts should call repository")
    void getPiiAlertsShouldReturnList() {
        List<AuditLog> expected = List.of(new AuditLog());
        when(auditLogRepository.findByPiiDetectedTrueOrderByCreatedAtDesc()).thenReturn(expected);

        List<AuditLog> result = auditLogService.getPiiAlerts();

        assertEquals(expected, result);
    }

    @Test
    @DisplayName("logRequest should handle repository exceptions gracefully")
    void logRequestShouldHandleExceptions() {
        doThrow(new RuntimeException("DB Down")).when(auditLogRepository).save(any());

        // Should not throw exception
        assertDoesNotThrow(() ->
            auditLogService.logRequest(new AuditLogService.AuditLogEntry(
                    "user", "p", "r", "m", false, false, 0, 200, 1L, "127.0.0.1"))
        );
    }

    @Test
    @DisplayName("getDashboardStats should return correct metrics")
    void getDashboardStatsShouldReturnMetrics() {
        when(auditLogRepository.count()).thenReturn(100L);
        when(auditLogRepository.countRequestsSince(any(LocalDateTime.class))).thenReturn(50L);
        when(auditLogRepository.countByPiiDetectedTrue()).thenReturn(5L);
        when(auditLogRepository.countByRateLimitedTrue()).thenReturn(2L);
        when(auditLogRepository.avgResponseTimeSince(any(LocalDateTime.class))).thenReturn(150.5);

        Map<String, Object> stats = auditLogService.getDashboardStats();

        assertEquals(100L, stats.get("totalRequests"));
        assertEquals(50L, stats.get("requestsLast24h"));
        assertEquals(5L, stats.get("piiDetections"));
        assertEquals(2L, stats.get("rateLimitedCount"));
        assertEquals(150.5, stats.get("avgResponseTimeMs"));
    }

    @Test
    @DisplayName("getDashboardStats should default avgResponseTime to 0.0 when null")
    void getDashboardStatsShouldDefaultAvgResponseTimeWhenNull() {
        when(auditLogRepository.count()).thenReturn(0L);
        when(auditLogRepository.countRequestsSince(any(LocalDateTime.class))).thenReturn(0L);
        when(auditLogRepository.countByPiiDetectedTrue()).thenReturn(0L);
        when(auditLogRepository.countByRateLimitedTrue()).thenReturn(0L);
        when(auditLogRepository.avgResponseTimeSince(any(LocalDateTime.class))).thenReturn(null);

        Map<String, Object> stats = auditLogService.getDashboardStats();

        assertEquals(0.0, stats.get("avgResponseTimeMs"));
    }

    @Test
    @DisplayName("logRequest should handle null prompt and response gracefully")
    void logRequestShouldHandleNullFields() {
        when(auditLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertDoesNotThrow(() ->
            auditLogService.logRequest(new AuditLogService.AuditLogEntry(
                    "user", null, null, "model", false, false, null, 200, 1L, "127.0.0.1"))
        );

        verify(auditLogRepository).save(any());
    }

    @Test
    @DisplayName("getBlockedRequests should call repository and return blocked request list")
    void getBlockedRequestsShouldReturnList() {
        List<AuditLog> expected = List.of(new AuditLog());
        when(auditLogRepository.findByBlockedByNotNullOrderByCreatedAtDesc()).thenReturn(expected);

        List<AuditLog> result = auditLogService.getBlockedRequests();

        assertEquals(expected, result);
        verify(auditLogRepository).findByBlockedByNotNullOrderByCreatedAtDesc();
    }

    @Test
    @DisplayName("logRequest should save all security fields from the full 17-param AuditLogEntry constructor")
    void logRequestShouldSaveSecurityFields() {
        when(auditLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        auditLogService.logRequest(new AuditLogService.AuditLogEntry(
                "user", "prompt", "response", "model", true, false,
                3, 200, 500L, "10.0.0.1",
                "NeMo:colang_policy_violation", 90L, "abc123hash",
                250, true, 0.8, "GROUNDED"
        ));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();

        assertEquals("NeMo:colang_policy_violation", saved.getBlockedBy());
        assertEquals(90L,         saved.getGuardrailLatencyMs());
        assertEquals("abc123hash", saved.getRequestHash());
        assertEquals(250,          saved.getTokensUsed());
        assertTrue(saved.isExcessiveTokenUsage());
        assertEquals(0.8,          saved.getGroundednessScore());
        assertEquals("GROUNDED",   saved.getGroundednessVerdict());
        assertTrue(saved.isPiiDetected());
    }

    @Test
    @DisplayName("getDashboardStats should default avgGroundednessScore to 0.0 when null")
    void getDashboardStatsShouldDefaultGroundednessWhenNull() {
        when(auditLogRepository.count()).thenReturn(0L);
        when(auditLogRepository.countRequestsSince(any(LocalDateTime.class))).thenReturn(0L);
        when(auditLogRepository.countByPiiDetectedTrue()).thenReturn(0L);
        when(auditLogRepository.countByRateLimitedTrue()).thenReturn(0L);
        when(auditLogRepository.avgResponseTimeSince(any(LocalDateTime.class))).thenReturn(0.0);
        when(auditLogRepository.avgGroundednessScoreSince(any(LocalDateTime.class))).thenReturn(null);

        Map<String, Object> stats = auditLogService.getDashboardStats();

        assertEquals(0.0, stats.get("avgGroundednessScore"),
                "null avgGroundednessScore should default to 0.0");
    }

    @Test
    @DisplayName("getDashboardStats should return actual avgGroundednessScore when non-null")
    void getDashboardStatsShouldReturnGroundednessScore() {
        when(auditLogRepository.count()).thenReturn(10L);
        when(auditLogRepository.countRequestsSince(any(LocalDateTime.class))).thenReturn(5L);
        when(auditLogRepository.countByPiiDetectedTrue()).thenReturn(1L);
        when(auditLogRepository.countByRateLimitedTrue()).thenReturn(0L);
        when(auditLogRepository.avgResponseTimeSince(any(LocalDateTime.class))).thenReturn(200.0);
        when(auditLogRepository.avgGroundednessScoreSince(any(LocalDateTime.class))).thenReturn(0.85);

        Map<String, Object> stats = auditLogService.getDashboardStats();

        assertEquals(0.85, stats.get("avgGroundednessScore"));
    }

    @Test
    @DisplayName("logRequest should not truncate prompt exactly at 4000 chars (boundary condition)")
    void logRequestShouldNotTruncateAtExactBoundary() {
        String exactLengthPrompt = "a".repeat(4000);
        when(auditLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        auditLogService.logRequest(new AuditLogService.AuditLogEntry(
                "user", exactLengthPrompt, "response", "model", false, false, 0, 200, 1L, "127.0.0.1"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        // Exactly 4000 chars should NOT be truncated (length <= 4000)
        assertFalse(captor.getValue().getPrompt().endsWith("...[truncated]"),
                "Prompt of exactly 4000 chars should not be truncated");
    }
}

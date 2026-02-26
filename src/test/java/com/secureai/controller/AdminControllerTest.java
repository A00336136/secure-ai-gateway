package com.secureai.controller;

import com.secureai.config.SecurityConfig;
import com.secureai.model.AuditLog;
import com.secureai.security.JwtAuthenticationFilter;
import com.secureai.service.AuditLogService;
import com.secureai.service.RateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({
    SecurityConfig.class,
    AdminControllerTest.MethodSecurityTestConfig.class,
    AdminControllerTest.TestJwtFilterConfig.class
})
@DisplayName("AdminController Tests")
class AdminControllerTest {

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityTestConfig {
        // empty: ensures @PreAuthorize processing for the slice (belt + suspenders)
    }

    @TestConfiguration
    static class TestJwtFilterConfig {
        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            // Pass-through filter: lets requests reach the controller in tests.
            // We do not validate tokens here because tests use @WithMockUser.
            return new JwtAuthenticationFilter(null) {
                @Override
                protected void doFilterInternal(
                        HttpServletRequest request,
                        HttpServletResponse response,
                        FilterChain filterChain
                ) throws ServletException, IOException {
                    filterChain.doFilter(request, response);
                }
            };
        }
    }

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AuditLogService auditLogService;

    @MockBean
    RateLimiterService rateLimiterService;

    @BeforeEach
    void clearMockInvocations() {
        // Spring may reuse the same mock instances across test methods in the cached context.
        // Clear recorded invocations so verifyNoInteractions(...) is reliable.
        clearInvocations(auditLogService, rateLimiterService);
    }

    @Nested
    @DisplayName("GET /admin/dashboard")
    class DashboardTests {

        @Test
        @WithMockUser(roles = "ADMIN")
        void adminCanGetDashboard() throws Exception {
            when(auditLogService.getDashboardStats()).thenReturn(Map.of("total", 123));

            mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(123));

            verify(auditLogService).getDashboardStats();
        }

        @Test
        @WithMockUser(roles = "USER")
        void nonAdminForbidden() throws Exception {
            mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isForbidden());

            verifyNoInteractions(auditLogService);
        }
    }

    @Nested
    @DisplayName("GET /admin/audit")
    class AuditTests {

        @Test
        @WithMockUser(roles = "ADMIN")
        void adminCanGetAuditLogsWithDefaults() throws Exception {
            Page<AuditLog> page = new PageImpl<>(List.of());
            when(auditLogService.getRecentLogs(0, 20)).thenReturn(page);

            mockMvc.perform(get("/admin/audit"))
                .andExpect(status().isOk());

            verify(auditLogService).getRecentLogs(0, 20);
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void adminCanGetAuditLogsWithParams() throws Exception {
            Page<AuditLog> page = new PageImpl<>(List.of());
            when(auditLogService.getRecentLogs(2, 50)).thenReturn(page);

            mockMvc.perform(get("/admin/audit")
                    .param("page", "2")
                    .param("size", "50"))
                .andExpect(status().isOk());

            verify(auditLogService).getRecentLogs(2, 50);
        }
    }

    @Nested
    @DisplayName("GET /admin/audit/pii-alerts")
    class PiiAlertsTests {

        @Test
        @WithMockUser(roles = "ADMIN")
        void adminCanGetPiiAlerts() throws Exception {
            when(auditLogService.getPiiAlerts()).thenReturn(List.of());

            mockMvc.perform(get("/admin/audit/pii-alerts"))
                .andExpect(status().isOk());

            verify(auditLogService).getPiiAlerts();
        }
    }

    @Nested
    @DisplayName("DELETE /admin/rate-limit/{username}")
    class RateLimitResetTests {

        @Test
        @WithMockUser(roles = "ADMIN")
        void adminCanResetRateLimit() throws Exception {
            doNothing().when(rateLimiterService).resetBucket("bob");

            mockMvc.perform(delete("/admin/rate-limit/bob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value(containsString("bob")));

            verify(rateLimiterService).resetBucket("bob");
        }
    }
}

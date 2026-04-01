package com.secureai.repository;

import com.secureai.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByUsernameOrderByCreatedAtDesc(String username, Pageable pageable);

    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<AuditLog> findByPiiDetectedTrueOrderByCreatedAtDesc();

    long countByPiiDetectedTrue();

    long countByRateLimitedTrue();

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.createdAt >= :since")
    long countRequestsSince(@Param("since") LocalDateTime since);

    @Query("SELECT a.username, COUNT(a) as reqCount FROM AuditLog a " +
           "WHERE a.createdAt >= :since GROUP BY a.username ORDER BY reqCount DESC")
    List<Object[]> topUsersSince(@Param("since") LocalDateTime since, Pageable pageable);

    @Query("SELECT AVG(a.durationMs) FROM AuditLog a WHERE a.createdAt >= :since")
    Double avgResponseTimeSince(@Param("since") LocalDateTime since);

    // ── Security & compliance queries (SOC 2 / HIPAA) ────────────────────────

    List<AuditLog> findByBlockedByNotNullOrderByCreatedAtDesc();

    long countByBlockedByNotNull();

    List<AuditLog> findByExcessiveTokenUsageTrueOrderByCreatedAtDesc();

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.groundednessVerdict = 'UNGROUNDED' AND a.createdAt >= :since")
    long countUngroundedResponsesSince(@Param("since") LocalDateTime since);

    @Query("SELECT AVG(a.groundednessScore) FROM AuditLog a WHERE a.groundednessScore IS NOT NULL AND a.createdAt >= :since")
    Double avgGroundednessScoreSince(@Param("since") LocalDateTime since);

    @Query("SELECT SUM(a.tokensUsed) FROM AuditLog a WHERE a.username = :username AND a.createdAt >= :since")
    Long totalTokensUsedByUserSince(@Param("username") String username, @Param("since") LocalDateTime since);
}

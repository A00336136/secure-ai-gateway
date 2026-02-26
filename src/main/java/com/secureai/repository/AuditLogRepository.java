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
}

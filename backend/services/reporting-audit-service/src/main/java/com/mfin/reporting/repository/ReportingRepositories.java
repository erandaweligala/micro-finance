package com.mfin.reporting.repository;

import com.mfin.reporting.domain.AuditLog;
import com.mfin.reporting.domain.LoanSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ReportingRepositories {

    private ReportingRepositories() {
    }

    public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

        boolean existsByTenantIdAndSourceEventId(UUID tenantId, UUID sourceEventId);

        /** The tail of the chain, whose hash the next entry links to. */
        Optional<AuditLog> findFirstByTenantIdOrderBySequenceNumberDesc(UUID tenantId);

        @Query("select coalesce(max(a.sequenceNumber), 0) from AuditLog a where a.tenantId = :tenantId")
        long maxSequence(@Param("tenantId") UUID tenantId);

        @Query("""
                select a from AuditLog a
                where a.tenantId = :tenantId
                  and (:action is null or a.action = :action)
                  and (:entityType is null or a.entityType = :entityType)
                  and (:entityId is null or a.entityId = :entityId)
                  and (:actorId is null or a.actorId = :actorId)
                  and (:from is null or a.occurredAt >= :from)
                  and (:to is null or a.occurredAt <= :to)
                order by a.sequenceNumber desc
                """)
        Page<AuditLog> search(@Param("tenantId") UUID tenantId,
                              @Param("action") String action,
                              @Param("entityType") String entityType,
                              @Param("entityId") UUID entityId,
                              @Param("actorId") UUID actorId,
                              @Param("from") Instant from,
                              @Param("to") Instant to,
                              Pageable pageable);

        /** Walked in order by the integrity verification job. */
        List<AuditLog> findByTenantIdOrderBySequenceNumberAsc(UUID tenantId);
    }

    public interface LoanSnapshotRepository extends JpaRepository<LoanSnapshot, UUID> {

        Optional<LoanSnapshot> findByTenantIdAndLoanAccountId(UUID tenantId, UUID loanAccountId);

        List<LoanSnapshot> findByTenantId(UUID tenantId);

        @Query("select count(s) from LoanSnapshot s where s.tenantId = :tenantId and s.status = :status")
        long countByStatus(@Param("tenantId") UUID tenantId, @Param("status") String status);

        @Query("""
                select coalesce(sum(s.outstandingPrincipal), 0) from LoanSnapshot s
                where s.tenantId = :tenantId and s.status <> 'CLOSED'
                """)
        BigDecimal totalOutstandingPrincipal(@Param("tenantId") UUID tenantId);

        @Query("""
                select coalesce(sum(s.outstandingPrincipal), 0) from LoanSnapshot s
                where s.tenantId = :tenantId and s.daysPastDue > :threshold
                """)
        BigDecimal principalAtRisk(@Param("tenantId") UUID tenantId,
                                   @Param("threshold") int threshold);

        @Query("""
                select coalesce(sum(s.principal), 0) from LoanSnapshot s
                where s.tenantId = :tenantId and s.disbursementDate between :from and :to
                """)
        BigDecimal disbursedBetween(@Param("tenantId") UUID tenantId,
                                    @Param("from") LocalDate from, @Param("to") LocalDate to);

        @Query("""
                select coalesce(sum(s.totalCollected), 0) from LoanSnapshot s
                where s.tenantId = :tenantId
                """)
        BigDecimal totalCollected(@Param("tenantId") UUID tenantId);
    }
}

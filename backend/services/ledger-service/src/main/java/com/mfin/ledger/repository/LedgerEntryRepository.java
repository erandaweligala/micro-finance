package com.mfin.ledger.repository;

import com.mfin.ledger.domain.LedgerEntry;
import com.mfin.ledger.domain.LedgerTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    /** Idempotency check: an event that is redelivered must not post a second line. */
    Optional<LedgerEntry> findByTenantIdAndSourceEventId(UUID tenantId, UUID sourceEventId);

    boolean existsByTenantIdAndSourceEventId(UUID tenantId, UUID sourceEventId);

    @Query("""
            select e from LedgerEntry e
            where e.tenantId = :tenantId
              and (:loanAccountId is null or e.loanAccountId = :loanAccountId)
              and (:customerId is null or e.customerId = :customerId)
              and (:type is null or e.transactionType = :type)
              and (:from is null or e.transactionDate >= :from)
              and (:to is null or e.transactionDate <= :to)
            order by e.transactionDate asc, e.postedAt asc
            """)
    Page<LedgerEntry> search(@Param("tenantId") UUID tenantId,
                             @Param("loanAccountId") UUID loanAccountId,
                             @Param("customerId") UUID customerId,
                             @Param("type") LedgerTransactionType type,
                             @Param("from") LocalDate from,
                             @Param("to") LocalDate to,
                             Pageable pageable);

    /** Unpaged variant for statement export, which must render the whole period in order. */
    @Query("""
            select e from LedgerEntry e
            where e.tenantId = :tenantId and e.loanAccountId = :loanAccountId
              and (:from is null or e.transactionDate >= :from)
              and (:to is null or e.transactionDate <= :to)
            order by e.transactionDate asc, e.postedAt asc
            """)
    List<LedgerEntry> findForStatement(@Param("tenantId") UUID tenantId,
                                       @Param("loanAccountId") UUID loanAccountId,
                                       @Param("from") LocalDate from,
                                       @Param("to") LocalDate to);

    @Query("""
            select coalesce(sum(e.debitAmount), 0) from LedgerEntry e
            where e.tenantId = :tenantId and e.loanAccountId = :loanAccountId
              and e.transactionDate < :before
            """)
    BigDecimal totalDebitsBefore(@Param("tenantId") UUID tenantId,
                                 @Param("loanAccountId") UUID loanAccountId,
                                 @Param("before") LocalDate before);

    @Query("""
            select coalesce(sum(e.creditAmount), 0) from LedgerEntry e
            where e.tenantId = :tenantId and e.loanAccountId = :loanAccountId
              and e.transactionDate < :before
            """)
    BigDecimal totalCreditsBefore(@Param("tenantId") UUID tenantId,
                                  @Param("loanAccountId") UUID loanAccountId,
                                  @Param("before") LocalDate before);
}

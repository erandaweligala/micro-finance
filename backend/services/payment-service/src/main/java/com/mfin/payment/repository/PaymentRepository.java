package com.mfin.payment.repository;

import com.mfin.payment.domain.Payment;
import com.mfin.payment.domain.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Payment> findByTenantIdAndReceiptNumber(UUID tenantId, String receiptNumber);

    @Query("""
            select p from Payment p
            where p.tenantId = :tenantId
              and (:loanAccountId is null or p.loanAccountId = :loanAccountId)
              and (:customerId is null or p.customerId = :customerId)
              and (:status is null or p.status = :status)
              and (:from is null or p.valueDate >= :from)
              and (:to is null or p.valueDate <= :to)
              and (:search is null
                   or lower(p.receiptNumber) like lower(concat('%', :search, '%'))
                   or lower(p.externalReference) like lower(concat('%', :search, '%')))
            """)
    Page<Payment> search(@Param("tenantId") UUID tenantId,
                         @Param("loanAccountId") UUID loanAccountId,
                         @Param("customerId") UUID customerId,
                         @Param("status") PaymentStatus status,
                         @Param("from") LocalDate from,
                         @Param("to") LocalDate to,
                         @Param("search") String search,
                         Pageable pageable);

    @Query("select coalesce(max(cast(substring(p.receiptNumber, 5) as integer)), 0) "
            + "from Payment p where p.tenantId = :tenantId")
    long maxReceiptSequence(@Param("tenantId") UUID tenantId);

    /** Cash-drawer total for a cashier's day, used by the end-of-day reconciliation report. */
    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.tenantId = :tenantId and p.receivedBy = :userId
              and p.valueDate = :date and p.status = com.mfin.payment.domain.PaymentStatus.POSTED
            """)
    java.math.BigDecimal totalCollectedBy(@Param("tenantId") UUID tenantId,
                                          @Param("userId") UUID userId,
                                          @Param("date") LocalDate date);
}

package com.mfin.loanaccount.repository;

import com.mfin.loanaccount.domain.AppliedRepayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppliedRepaymentRepository extends JpaRepository<AppliedRepayment, UUID> {

    /** The idempotency lookup: a payment is applied to a loan at most once. */
    Optional<AppliedRepayment> findByTenantIdAndPaymentId(UUID tenantId, UUID paymentId);

    List<AppliedRepayment> findByTenantIdAndLoanAccountIdOrderByCreatedAtDesc(
            UUID tenantId, UUID loanAccountId);
}

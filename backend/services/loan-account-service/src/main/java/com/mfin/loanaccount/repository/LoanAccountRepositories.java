package com.mfin.loanaccount.repository;

import com.mfin.loanaccount.domain.LoanAccount;
import com.mfin.loanaccount.domain.LoanAccountStatus;
import com.mfin.loanaccount.domain.ScheduleInstallment;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class LoanAccountRepositories {

    private LoanAccountRepositories() {
    }

    public interface LoanAccountRepository extends JpaRepository<LoanAccount, UUID> {

        Optional<LoanAccount> findByIdAndTenantId(UUID id, UUID tenantId);

        Optional<LoanAccount> findByTenantIdAndApplicationId(UUID tenantId, UUID applicationId);

        Optional<LoanAccount> findByTenantIdAndAccountNumber(UUID tenantId, String accountNumber);

        /**
         * Locks the account row for the duration of a repayment.
         *
         * <p>Optimistic locking alone would let two concurrent repayments both read the same
         * balance and one fail late with a conflict the cashier cannot act on. A pessimistic
         * lock serialises them instead, so the second simply waits and then allocates against
         * the balance the first left behind.</p>
         */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select a from LoanAccount a where a.id = :id and a.tenantId = :tenantId")
        Optional<LoanAccount> findForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

        @Query("""
                select a from LoanAccount a
                where a.tenantId = :tenantId
                  and (:status is null or a.status = :status)
                  and (:customerId is null or a.customerId = :customerId)
                  and (:branchId is null or a.branchId = :branchId)
                  and (:search is null
                       or lower(a.accountNumber) like lower(concat('%', :search, '%')))
                """)
        Page<LoanAccount> search(@Param("tenantId") UUID tenantId,
                                 @Param("status") LoanAccountStatus status,
                                 @Param("customerId") UUID customerId,
                                 @Param("branchId") UUID branchId,
                                 @Param("search") String search,
                                 Pageable pageable);

        /** Portfolio-wide arrears sweep; deliberately not tenant-scoped, the job iterates all. */
        @Query("""
                select distinct a from LoanAccount a
                where a.status in (com.mfin.loanaccount.domain.LoanAccountStatus.ACTIVE,
                                   com.mfin.loanaccount.domain.LoanAccountStatus.OVERDUE)
                """)
        List<LoanAccount> findOpenAccounts(Pageable pageable);

        @Query("select coalesce(max(cast(substring(a.accountNumber, 5) as integer)), 0) "
                + "from LoanAccount a where a.tenantId = :tenantId")
        long maxAccountSequence(@Param("tenantId") UUID tenantId);

        long countByTenantIdAndStatus(UUID tenantId, LoanAccountStatus status);

        @Query("""
                select coalesce(sum(a.outstandingPrincipal), 0) from LoanAccount a
                where a.tenantId = :tenantId and a.status <> com.mfin.loanaccount.domain.LoanAccountStatus.CLOSED
                """)
        java.math.BigDecimal totalOutstandingPrincipal(@Param("tenantId") UUID tenantId);
    }

    public interface ScheduleInstallmentRepository extends JpaRepository<ScheduleInstallment, UUID> {

        List<ScheduleInstallment> findByTenantIdAndLoanAccountIdOrderByInstallmentNumberAsc(
                UUID tenantId, UUID loanAccountId);

        Page<ScheduleInstallment> findByTenantIdAndLoanAccountIdOrderByInstallmentNumberAsc(
                UUID tenantId, UUID loanAccountId, Pageable pageable);

        /** Installments that are past due and not settled, for the arrears job. */
        @Query("""
                select i from ScheduleInstallment i
                where i.loanAccountId = :loanAccountId
                  and i.dueDate < :asOf
                  and i.status <> com.mfin.loanaccount.domain.InstallmentStatus.PAID
                  and i.status <> com.mfin.loanaccount.domain.InstallmentStatus.WAIVED
                order by i.dueDate asc
                """)
        List<ScheduleInstallment> findOverdue(@Param("loanAccountId") UUID loanAccountId,
                                              @Param("asOf") LocalDate asOf);

        long countByTenantIdAndLoanAccountId(UUID tenantId, UUID loanAccountId);
    }
}

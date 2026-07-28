package com.mfin.loanaccount.application;

import com.mfin.common.error.ApiExceptions;
import com.mfin.common.tenant.TenantContext;
import com.mfin.loan.engine.Money;
import com.mfin.loanaccount.domain.AllocationOrder;
import com.mfin.loanaccount.domain.AppliedRepayment;
import com.mfin.loanaccount.domain.LoanAccount;
import com.mfin.loanaccount.domain.ScheduleInstallment;
import com.mfin.loanaccount.repository.AppliedRepaymentRepository;
import com.mfin.loanaccount.repository.LoanAccountRepositories.LoanAccountRepository;
import com.mfin.loanaccount.repository.LoanAccountRepositories.ScheduleInstallmentRepository;
import com.mfin.loanaccount.web.dto.RepaymentDtos.ApplyRepaymentRequest;
import com.mfin.loanaccount.web.dto.RepaymentDtos.RepaymentResult;
import com.mfin.loanaccount.web.dto.RepaymentDtos.ReverseRepaymentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Applies and reverses repayments against a loan.
 *
 * <p>This is the authoritative home of repayment posting: the payment service owns the receipt,
 * the channel and idempotency, but the balances live here and so does the allocation. Every
 * method runs in one transaction that moves the account totals and the installment rows
 * together - a partial application would corrupt the loan book.</p>
 */
@Service
public class RepaymentService {

    private static final Logger log = LoggerFactory.getLogger(RepaymentService.class);

    private final LoanAccountRepository accountRepository;
    private final ScheduleInstallmentRepository installmentRepository;
    private final AppliedRepaymentRepository appliedRepaymentRepository;
    private final RepaymentAllocator allocator;
    private final AllocationOrder defaultAllocationOrder;

    public RepaymentService(LoanAccountRepository accountRepository,
                            ScheduleInstallmentRepository installmentRepository,
                            AppliedRepaymentRepository appliedRepaymentRepository,
                            RepaymentAllocator allocator,
                            @Value("${mfin.loan.allocation-order:PENALTY_FEE_INTEREST_PRINCIPAL}")
                            AllocationOrder defaultAllocationOrder) {
        this.accountRepository = accountRepository;
        this.installmentRepository = installmentRepository;
        this.appliedRepaymentRepository = appliedRepaymentRepository;
        this.allocator = allocator;
        this.defaultAllocationOrder = defaultAllocationOrder;
    }

    /**
     * Applies a repayment.
     *
     * <p>Idempotent on {@code paymentId}: the payment service retries on network failure, and a
     * retry must not move the balances twice. The account row is locked for the duration so
     * concurrent repayments serialise rather than racing on the same balance.</p>
     */
    @Transactional
    public RepaymentResult apply(UUID accountId, ApplyRepaymentRequest request) {
        UUID tenantId = TenantContext.requireTenantId();

        var alreadyApplied = appliedRepaymentRepository
                .findByTenantIdAndPaymentId(tenantId, request.paymentId());
        if (alreadyApplied.isPresent()) {
            log.info("Payment {} was already applied to loan {}; replaying the recorded result",
                    request.paymentId(), accountId);
            return toResult(alreadyApplied.get(), requireAccount(accountId, tenantId));
        }

        LoanAccount account = accountRepository.findForUpdate(accountId, tenantId)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("Loan account", accountId));
        account.requireOpen();

        if (!Money.isPositive(request.amount())) {
            throw new ApiExceptions.BusinessRuleException("A repayment must be greater than zero");
        }
        if (request.valueDate().isBefore(account.getDisbursementDate())) {
            throw new ApiExceptions.BusinessRuleException(
                    "A repayment cannot be dated before the loan was disbursed");
        }
        if (request.valueDate().isAfter(LocalDate.now())) {
            throw new ApiExceptions.BusinessRuleException("A repayment cannot be dated in the future");
        }

        List<ScheduleInstallment> installments = installmentRepository
                .findByTenantIdAndLoanAccountIdOrderByInstallmentNumberAsc(tenantId, accountId);
        AllocationOrder order = request.allocationOrder() != null
                ? request.allocationOrder() : defaultAllocationOrder;

        RepaymentAllocator.AllocationPlan plan =
                allocator.allocate(installments, request.amount(), request.valueDate(), order);

        Map<UUID, ScheduleInstallment> byId = installments.stream()
                .collect(Collectors.toMap(ScheduleInstallment::getId, Function.identity()));
        for (var allocation : plan.allocations()) {
            ScheduleInstallment installment = byId.get(allocation.installmentId());
            installment.allocate(allocation.principal(), allocation.interest(), allocation.fee(),
                    allocation.penalty(), request.valueDate());
        }
        installmentRepository.saveAll(installments);

        // Only genuinely unapplied money becomes a credit; money that settled a future
        // installment has already reduced that installment's buckets.
        account.applyAllocation(plan.principal(), plan.interest(), plan.fee(), plan.penalty(),
                plan.excess(), request.valueDate());
        accountRepository.save(account);

        AppliedRepayment record = new AppliedRepayment(accountId, request.paymentId(),
                request.amount(), plan.principal(), plan.interest(), plan.fee(), plan.penalty(),
                plan.excess(), request.valueDate());
        record.setTenantId(tenantId);
        appliedRepaymentRepository.save(record);

        log.info("Applied {} to loan {}: principal {}, interest {}, fees {}, penalty {}, credit {}",
                request.amount(), account.getAccountNumber(), plan.principal(), plan.interest(),
                plan.fee(), plan.penalty(), plan.excess());
        return toResult(record, account);
    }

    /**
     * Reverses a previously applied repayment.
     *
     * <p>The stored allocation is replayed in reverse rather than recomputed: recomputing would
     * produce a different split if penalties have accrued since, and the loan would not return
     * to its pre-payment state.</p>
     */
    @Transactional
    public RepaymentResult reverse(UUID accountId, UUID paymentId, ReverseRepaymentRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        AppliedRepayment applied = appliedRepaymentRepository
                .findByTenantIdAndPaymentId(tenantId, paymentId)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException(
                        "Applied repayment for payment", paymentId));
        if (applied.isReversed()) {
            throw new ApiExceptions.BusinessRuleException("This repayment has already been reversed");
        }

        LoanAccount account = accountRepository.findForUpdate(accountId, tenantId)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("Loan account", accountId));

        List<ScheduleInstallment> installments = installmentRepository
                .findByTenantIdAndLoanAccountIdOrderByInstallmentNumberAsc(tenantId, accountId);
        LocalDate today = LocalDate.now();

        // Unwind installment by installment, oldest first, in the same bucket proportions.
        BigDecimal principalLeft = applied.getPrincipalAllocated();
        BigDecimal interestLeft = applied.getInterestAllocated();
        BigDecimal feeLeft = applied.getFeeAllocated();
        BigDecimal penaltyLeft = applied.getPenaltyAllocated();

        for (ScheduleInstallment installment : installments) {
            if (!Money.isPositive(principalLeft) && !Money.isPositive(interestLeft)
                    && !Money.isPositive(feeLeft) && !Money.isPositive(penaltyLeft)) {
                break;
            }
            BigDecimal principal = Money.min(principalLeft, installment.getPrincipalPaid());
            BigDecimal interest = Money.min(interestLeft, installment.getInterestPaid());
            BigDecimal fee = Money.min(feeLeft, installment.getFeePaid());
            BigDecimal penalty = Money.min(penaltyLeft, installment.getPenaltyPaid());
            if (Money.isPositive(principal) || Money.isPositive(interest)
                    || Money.isPositive(fee) || Money.isPositive(penalty)) {
                installment.reverseAllocation(principal, interest, fee, penalty, today);
                principalLeft = principalLeft.subtract(principal);
                interestLeft = interestLeft.subtract(interest);
                feeLeft = feeLeft.subtract(fee);
                penaltyLeft = penaltyLeft.subtract(penalty);
            }
        }
        installmentRepository.saveAll(installments);

        account.reverseAllocation(applied.getPrincipalAllocated(), applied.getInterestAllocated(),
                applied.getFeeAllocated(), applied.getPenaltyAllocated(), applied.getExcessAmount());
        accountRepository.save(account);

        applied.markReversed(TenantContext.require().userId(), request.reason());
        appliedRepaymentRepository.save(applied);

        log.warn("Reversed payment {} on loan {} ({}); authorised by {}", paymentId,
                account.getAccountNumber(), request.reason(), TenantContext.require().userId());
        return toResult(applied, account);
    }

    private LoanAccount requireAccount(UUID accountId, UUID tenantId) {
        return accountRepository.findByIdAndTenantId(accountId, tenantId)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("Loan account", accountId));
    }

    private RepaymentResult toResult(AppliedRepayment record, LoanAccount account) {
        return new RepaymentResult(record.getPaymentId(), account.getId(),
                account.getAccountNumber(), account.getCustomerId(), account.getCurrency(),
                record.getAmount(), record.getPenaltyAllocated(),
                record.getInterestAllocated(), record.getFeeAllocated(),
                record.getPrincipalAllocated(), record.getExcessAmount(),
                account.getOutstandingPrincipal(), account.totalOutstanding(),
                account.getStatus(), record.isReversed());
    }
}

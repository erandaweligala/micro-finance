package com.mfin.loanaccount.application;

import com.mfin.common.error.ApiExceptions;
import com.mfin.common.events.LoanEvents;
import com.mfin.common.outbox.DomainEventPublisher;
import com.mfin.common.tenant.TenantContext;
import com.mfin.common.web.PageResponse;
import com.mfin.loan.engine.LoanCalculationRequest;
import com.mfin.loan.engine.LoanCalculator;
import com.mfin.loan.engine.LoanSchedule;
import com.mfin.loan.engine.ScheduledInstallment;
import com.mfin.loanaccount.client.ProductClient;
import com.mfin.loanaccount.domain.LoanAccount;
import com.mfin.loanaccount.domain.LoanAccountStatus;
import com.mfin.loanaccount.domain.ScheduleInstallment;
import com.mfin.loanaccount.repository.LoanAccountRepositories.LoanAccountRepository;
import com.mfin.loanaccount.repository.LoanAccountRepositories.ScheduleInstallmentRepository;
import com.mfin.loanaccount.web.dto.LoanAccountDtos.AccountResponse;
import com.mfin.loanaccount.web.dto.LoanAccountDtos.AccountSummary;
import com.mfin.loanaccount.web.dto.LoanAccountDtos.InstallmentResponse;
import com.mfin.loanaccount.web.dto.LoanAccountDtos.PayoffQuote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Opens loan accounts and serves their schedules and balances.
 *
 * <p>Account opening is driven by {@code loan.disbursed.v1} rather than by an API call, so the
 * loan account is created exactly when the money is recorded as released. The operation is
 * idempotent on {@code applicationId} because at-least-once delivery means the event <em>will</em>
 * occasionally arrive twice.</p>
 */
@Service
public class LoanAccountService {

    private static final Logger log = LoggerFactory.getLogger(LoanAccountService.class);

    private final LoanAccountRepository accountRepository;
    private final ScheduleInstallmentRepository installmentRepository;
    private final ProductClient productClient;
    private final LoanCalculator calculator;
    private final DomainEventPublisher eventPublisher;

    public LoanAccountService(LoanAccountRepository accountRepository,
                              ScheduleInstallmentRepository installmentRepository,
                              ProductClient productClient,
                              LoanCalculator calculator,
                              DomainEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.installmentRepository = installmentRepository;
        this.productClient = productClient;
        this.calculator = calculator;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Opens the loan account and materialises its repayment schedule.
     *
     * @return the account, whether newly created or already present from an earlier delivery
     */
    @Transactional
    public LoanAccount openFromDisbursement(LoanEvents.LoanDisbursed event) {
        UUID tenantId = event.tenantId();

        var existing = accountRepository.findByTenantIdAndApplicationId(tenantId, event.applicationId());
        if (existing.isPresent()) {
            log.info("Loan account already exists for application {}; ignoring duplicate event",
                    event.applicationId());
            return existing.get();
        }

        var product = productClient.fetchInternal(tenantId, event.productId());

        LoanAccount account = new LoanAccount(nextAccountNumber(tenantId), event.applicationId(),
                event.customerId(), event.productId(), product.currency(), product.currencyScale(),
                event.principal());
        account.setTenantId(tenantId);

        LoanCalculationRequest request = LoanCalculationRequest.builder()
                .principal(event.principal())
                .annualInterestRate(product.defaultAnnualRate())
                .numberOfInstallments(product.defaultInstallments())
                .frequency(product.repaymentFrequency())
                .interestMethod(product.interestMethod())
                .disbursementDate(event.disbursementDate())
                .firstRepaymentDate(event.firstRepaymentDate())
                .feeType(product.feeType())
                .feeValue(product.feeValue())
                .feeCollection(product.feeCollection())
                .dayCount(product.dayCount())
                .currencyScale(product.currencyScale())
                .build();
        LoanSchedule schedule = calculator.generate(request);

        account.setContract(schedule.totalInterest(), schedule.totalFees(), schedule.totalRepayable(),
                schedule.regularInstallment(), product.defaultAnnualRate(), product.interestMethod(),
                product.repaymentFrequency(), schedule.numberOfInstallments());
        account.setDates(event.disbursementDate(), schedule.effectiveFirstDueDate(),
                schedule.maturityDate());
        LoanAccount saved = accountRepository.save(account);

        List<ScheduleInstallment> installments = new ArrayList<>();
        for (ScheduledInstallment row : schedule.installments()) {
            ScheduleInstallment installment = new ScheduleInstallment(saved.getId(),
                    row.installmentNumber(), row.dueDate());
            installment.setTenantId(tenantId);
            installment.setAmounts(row.openingBalance(), row.principal(), row.interest(),
                    row.fee(), row.closingBalance(), row.graceInstallment());
            installments.add(installment);
        }
        installmentRepository.saveAll(installments);

        eventPublisher.publish(new LoanEvents.LoanAccountOpened(UUID.randomUUID(), tenantId,
                saved.getId(), saved.getAccountNumber(), event.applicationId(), event.customerId(),
                saved.getPrincipal(), saved.getTotalRepayable(), saved.getMaturityDate(),
                Instant.now()));

        log.info("Opened loan account {} for application {} with {} installments",
                saved.getAccountNumber(), event.applicationId(), installments.size());
        return saved;
    }

    @Transactional(readOnly = true)
    public PageResponse<AccountSummary> search(LoanAccountStatus status, UUID customerId,
                                               UUID branchId, String query, Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();
        String normalised = query == null || query.isBlank() ? null : query.trim();
        return PageResponse.from(accountRepository.search(tenantId, status, customerId, branchId,
                normalised, pageable), AccountSummary::from);
    }

    @Transactional(readOnly = true)
    public AccountResponse get(UUID accountId) {
        return AccountResponse.from(require(accountId));
    }

    @Transactional(readOnly = true)
    public PageResponse<InstallmentResponse> schedule(UUID accountId, Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();
        require(accountId);
        return PageResponse.from(
                installmentRepository.findByTenantIdAndLoanAccountIdOrderByInstallmentNumberAsc(
                        tenantId, accountId, pageable), InstallmentResponse::from);
    }

    /**
     * What it would cost to settle the loan today.
     *
     * <p>Early settlement pays the outstanding principal plus interest and charges accrued to
     * date - not the remaining scheduled interest, which the borrower has not yet incurred.</p>
     */
    @Transactional(readOnly = true)
    public PayoffQuote payoffQuote(UUID accountId, LocalDate asOf) {
        UUID tenantId = TenantContext.requireTenantId();
        LoanAccount account = require(accountId);
        LocalDate settlementDate = asOf == null ? LocalDate.now() : asOf;

        List<ScheduleInstallment> installments = installmentRepository
                .findByTenantIdAndLoanAccountIdOrderByInstallmentNumberAsc(tenantId, accountId);

        BigDecimal interestDueToDate = BigDecimal.ZERO;
        BigDecimal feesDueToDate = BigDecimal.ZERO;
        BigDecimal penaltyDue = BigDecimal.ZERO;
        for (ScheduleInstallment installment : installments) {
            penaltyDue = penaltyDue.add(installment.outstandingPenalty());
            if (installment.isDueBy(settlementDate)) {
                interestDueToDate = interestDueToDate.add(installment.outstandingInterest());
                feesDueToDate = feesDueToDate.add(installment.outstandingFee());
            }
        }

        BigDecimal settlement = account.getOutstandingPrincipal()
                .add(interestDueToDate).add(feesDueToDate).add(penaltyDue)
                .subtract(account.getAdvanceBalance());

        return new PayoffQuote(account.getId(), account.getAccountNumber(), settlementDate,
                account.getOutstandingPrincipal(), interestDueToDate, feesDueToDate, penaltyDue,
                account.getAdvanceBalance(), settlement.max(BigDecimal.ZERO),
                account.totalOutstanding(), account.getCurrency());
    }

    private LoanAccount require(UUID accountId) {
        UUID tenantId = TenantContext.requireTenantId();
        return accountRepository.findByIdAndTenantId(accountId, tenantId)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("Loan account", accountId));
    }

    private String nextAccountNumber(UUID tenantId) {
        return String.format("LN-%08d", accountRepository.maxAccountSequence(tenantId) + 1);
    }
}

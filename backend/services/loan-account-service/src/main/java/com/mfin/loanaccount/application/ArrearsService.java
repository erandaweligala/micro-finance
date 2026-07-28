package com.mfin.loanaccount.application;

import com.mfin.common.events.LoanEvents;
import com.mfin.common.outbox.DomainEventPublisher;
import com.mfin.common.tenant.TenantContext;
import com.mfin.common.tenant.TenantPrincipal;
import com.mfin.loan.engine.Money;
import com.mfin.loanaccount.client.ProductClient;
import com.mfin.loanaccount.domain.LoanAccount;
import com.mfin.loanaccount.domain.ScheduleInstallment;
import com.mfin.loanaccount.repository.LoanAccountRepositories.LoanAccountRepository;
import com.mfin.loanaccount.repository.LoanAccountRepositories.ScheduleInstallmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * The daily arrears run: ages overdue installments, accrues penalties and reclassifies loans.
 *
 * <p>Runs once per day rather than on read, because a penalty is a financial event that has to
 * be recorded, dated and posted to the ledger - not a number computed on the fly that changes
 * depending on when someone happens to look.</p>
 */
@Service
public class ArrearsService {

    private static final Logger log = LoggerFactory.getLogger(ArrearsService.class);
    private static final int BATCH_SIZE = 500;

    private final LoanAccountRepository accountRepository;
    private final ScheduleInstallmentRepository installmentRepository;
    private final ProductClient productClient;
    private final DomainEventPublisher eventPublisher;

    public ArrearsService(LoanAccountRepository accountRepository,
                          ScheduleInstallmentRepository installmentRepository,
                          ProductClient productClient,
                          DomainEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.installmentRepository = installmentRepository;
        this.productClient = productClient;
        this.eventPublisher = eventPublisher;
    }

    /** Fires shortly after midnight, once the previous day's payments have settled. */
    @Scheduled(cron = "${mfin.loan.arrears-cron:0 15 1 * * *}")
    public void runDailyArrears() {
        LocalDate today = LocalDate.now();
        log.info("Starting arrears run for {}", today);
        int processed = 0;
        List<LoanAccount> batch = accountRepository.findOpenAccounts(PageRequest.of(0, BATCH_SIZE));
        for (LoanAccount account : batch) {
            try {
                TenantContext.runAs(TenantPrincipal.system(account.getTenantId()),
                        () -> assessAccount(account.getId(), account.getTenantId(), today));
                processed++;
            } catch (Exception ex) {
                // One bad loan must not stop the portfolio run.
                log.error("Arrears assessment failed for loan {}", account.getAccountNumber(), ex);
            }
        }
        log.info("Arrears run complete: {} loans assessed", processed);
    }

    /**
     * Assesses one loan: recomputes days past due and the overdue amount, accrues any penalty
     * that has become chargeable, and reclassifies the loan.
     */
    @Transactional
    public void assessAccount(UUID accountId, UUID tenantId, LocalDate today) {
        LoanAccount account = accountRepository.findForUpdate(accountId, tenantId).orElse(null);
        if (account == null) {
            return;
        }
        var product = productClient.fetchInternal(tenantId, account.getProductId());
        List<ScheduleInstallment> overdue = installmentRepository.findOverdue(accountId, today);

        BigDecimal overdueAmount = BigDecimal.ZERO;
        int daysPastDue = 0;
        for (ScheduleInstallment installment : overdue) {
            overdueAmount = overdueAmount.add(installment.totalOutstanding());
            daysPastDue = Math.max(daysPastDue,
                    (int) ChronoUnit.DAYS.between(installment.getDueDate(), today));
        }

        BigDecimal penaltyAccrued = accruePenalties(account, overdue, product, today);
        if (Money.isPositive(penaltyAccrued)) {
            account.accruePenalty(penaltyAccrued);
            overdueAmount = overdueAmount.add(penaltyAccrued);
        }

        int previousDaysPastDue = account.getDaysPastDue();
        account.updateArrears(daysPastDue, overdueAmount, product.daysToDefault());
        accountRepository.save(account);
        installmentRepository.saveAll(overdue);

        // Emitted on transition only, so dunning does not message the borrower every night.
        if (daysPastDue > 0 && previousDaysPastDue == 0) {
            eventPublisher.publish(new LoanEvents.LoanOverdue(UUID.randomUUID(), tenantId,
                    accountId, account.getCustomerId(), daysPastDue, overdueAmount, Instant.now()));
        }
    }

    /**
     * Accrues one day of penalty on each installment that is past both its due date and the
     * product's penalty grace period.
     *
     * @return the total penalty added by this run
     */
    private BigDecimal accruePenalties(LoanAccount account, List<ScheduleInstallment> overdue,
                                       ProductClient.ProductTerms product, LocalDate today) {
        if (!Money.isPositive(product.penaltyAnnualRate())) {
            return BigDecimal.ZERO;
        }
        BigDecimal dailyRate = product.penaltyAnnualRate()
                .divide(Money.HUNDRED, Money.MC)
                .divide(BigDecimal.valueOf(365), Money.MC);

        BigDecimal total = BigDecimal.ZERO;
        for (ScheduleInstallment installment : overdue) {
            long daysLate = ChronoUnit.DAYS.between(installment.getDueDate(), today);
            if (daysLate <= product.penaltyGraceDays()) {
                continue;
            }
            BigDecimal basis = switch (product.penaltyBasis()) {
                case "OVERDUE_PRINCIPAL" -> installment.outstandingPrincipal();
                case "INSTALLMENT_AMOUNT" -> installment.totalDue();
                // OVERDUE_TOTAL and anything unrecognised fall back to the full arrears,
                // which is the most common configuration.
                default -> installment.outstandingPrincipal().add(installment.outstandingInterest());
            };
            if (!Money.isPositive(basis)) {
                continue;
            }
            BigDecimal penalty = basis.multiply(dailyRate, Money.MC)
                    .setScale(account.getCurrencyScale(), RoundingMode.HALF_UP);
            if (Money.isPositive(penalty)) {
                installment.addPenalty(penalty);
                total = total.add(penalty);
            }
        }
        return total;
    }
}

package com.mfin.loanaccount.web.dto;

import com.mfin.loan.engine.InterestMethod;
import com.mfin.loan.engine.RepaymentFrequency;
import com.mfin.loanaccount.domain.InstallmentStatus;
import com.mfin.loanaccount.domain.LoanAccount;
import com.mfin.loanaccount.domain.LoanAccountStatus;
import com.mfin.loanaccount.domain.ScheduleInstallment;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Loan account and schedule payloads. */
public final class LoanAccountDtos {

    private LoanAccountDtos() {
    }

    @Schema(description = "A live loan with its contracted terms and current balances")
    public record AccountResponse(
            UUID id,
            String accountNumber,
            UUID applicationId,
            UUID customerId,
            UUID productId,
            String currency,
            BigDecimal principal,
            BigDecimal totalInterest,
            BigDecimal totalFees,
            BigDecimal totalRepayable,
            BigDecimal installmentAmount,
            BigDecimal annualInterestRate,
            InterestMethod interestMethod,
            RepaymentFrequency repaymentFrequency,
            int numberOfInstallments,
            BigDecimal outstandingPrincipal,
            BigDecimal outstandingInterest,
            BigDecimal outstandingFees,
            BigDecimal outstandingPenalty,
            @Schema(description = "Everything still owed, net of any credit balance")
            BigDecimal totalOutstanding,
            BigDecimal principalPaid,
            BigDecimal interestPaid,
            BigDecimal feesPaid,
            BigDecimal penaltyPaid,
            @Schema(description = "Overpayment held against future installments")
            BigDecimal advanceBalance,
            LocalDate disbursementDate,
            LocalDate firstRepaymentDate,
            LocalDate maturityDate,
            LocalDate lastPaymentDate,
            int daysPastDue,
            BigDecimal overdueAmount,
            LoanAccountStatus status,
            LocalDate closedOn,
            UUID branchId,
            UUID loanOfficerId
    ) {
        public static AccountResponse from(LoanAccount account) {
            return new AccountResponse(account.getId(), account.getAccountNumber(),
                    account.getApplicationId(), account.getCustomerId(), account.getProductId(),
                    account.getCurrency(), account.getPrincipal(), account.getTotalInterest(),
                    account.getTotalFees(), account.getTotalRepayable(),
                    account.getInstallmentAmount(), account.getAnnualInterestRate(),
                    account.getInterestMethod(), account.getRepaymentFrequency(),
                    account.getNumberOfInstallments(), account.getOutstandingPrincipal(),
                    account.getOutstandingInterest(), account.getOutstandingFees(),
                    account.getOutstandingPenalty(), account.totalOutstanding(),
                    account.getPrincipalPaid(), account.getInterestPaid(), account.getFeesPaid(),
                    account.getPenaltyPaid(), account.getAdvanceBalance(),
                    account.getDisbursementDate(), account.getFirstRepaymentDate(),
                    account.getMaturityDate(), account.getLastPaymentDate(),
                    account.getDaysPastDue(), account.getOverdueAmount(), account.getStatus(),
                    account.getClosedOn(), account.getBranchId(), account.getLoanOfficerId());
        }
    }

    @Schema(description = "Compact loan record for portfolio lists")
    public record AccountSummary(
            UUID id,
            String accountNumber,
            UUID customerId,
            String currency,
            BigDecimal principal,
            BigDecimal outstandingPrincipal,
            BigDecimal totalOutstanding,
            BigDecimal installmentAmount,
            LocalDate maturityDate,
            int daysPastDue,
            LoanAccountStatus status
    ) {
        public static AccountSummary from(LoanAccount account) {
            return new AccountSummary(account.getId(), account.getAccountNumber(),
                    account.getCustomerId(), account.getCurrency(), account.getPrincipal(),
                    account.getOutstandingPrincipal(), account.totalOutstanding(),
                    account.getInstallmentAmount(), account.getMaturityDate(),
                    account.getDaysPastDue(), account.getStatus());
        }
    }

    @Schema(description = "One installment of a live loan, showing due and paid amounts per bucket")
    public record InstallmentResponse(
            UUID id,
            int installmentNumber,
            LocalDate dueDate,
            BigDecimal openingBalance,
            BigDecimal principalDue,
            BigDecimal interestDue,
            BigDecimal feeDue,
            BigDecimal penaltyDue,
            BigDecimal totalDue,
            BigDecimal principalPaid,
            BigDecimal interestPaid,
            BigDecimal feePaid,
            BigDecimal penaltyPaid,
            BigDecimal totalPaid,
            BigDecimal totalOutstanding,
            BigDecimal closingBalance,
            boolean graceInstallment,
            InstallmentStatus status,
            LocalDate settledOn
    ) {
        public static InstallmentResponse from(ScheduleInstallment installment) {
            return new InstallmentResponse(installment.getId(), installment.getInstallmentNumber(),
                    installment.getDueDate(), installment.getOpeningBalance(),
                    installment.getPrincipalDue(), installment.getInterestDue(),
                    installment.getFeeDue(), installment.getPenaltyDue(), installment.totalDue(),
                    installment.getPrincipalPaid(), installment.getInterestPaid(),
                    installment.getFeePaid(), installment.getPenaltyPaid(), installment.totalPaid(),
                    installment.totalOutstanding(), installment.getClosingBalance(),
                    installment.isGraceInstallment(), installment.getStatus(),
                    installment.getSettledOn());
        }
    }

    @Schema(description = """
            Cost of settling the loan on a given date. Early settlement pays the outstanding
            principal plus interest and charges accrued to that date - not the remaining
            scheduled interest, which has not yet been incurred.
            """)
    public record PayoffQuote(
            UUID loanAccountId,
            String accountNumber,
            LocalDate asOf,
            BigDecimal outstandingPrincipal,
            BigDecimal interestDueToDate,
            BigDecimal feesDueToDate,
            BigDecimal penaltyDue,
            BigDecimal creditBalance,
            @Schema(description = "Amount payable to close the loan on this date")
            BigDecimal settlementAmount,
            @Schema(description = "Full contractual balance if run to maturity")
            BigDecimal totalOutstanding,
            String currency
    ) {
    }
}

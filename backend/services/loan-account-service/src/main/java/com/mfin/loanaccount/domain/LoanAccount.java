package com.mfin.loanaccount.domain;

import com.mfin.common.error.ApiExceptions;
import com.mfin.common.persistence.TenantAwareEntity;
import com.mfin.loan.engine.InterestMethod;
import com.mfin.loan.engine.Money;
import com.mfin.loan.engine.RepaymentFrequency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A live loan.
 *
 * <p>The outstanding balances are maintained here as running totals rather than being summed
 * from the schedule on every read: a loan book query that had to aggregate millions of
 * installment rows would not survive contact with a real portfolio. The totals are only ever
 * moved by {@link #applyAllocation}, and {@code @Version} optimistic locking is what prevents
 * two concurrent repayments from both reading the same balance and each writing their own
 * result.</p>
 */
@Entity
@Table(name = "loan_account",
        uniqueConstraints = {
                @UniqueConstraint(name = "ux_loan_account_tenant_number",
                        columnNames = {"tenant_id", "account_number"}),
                // One account per application: this is what makes disbursement-event
                // consumption idempotent.
                @UniqueConstraint(name = "ux_loan_account_application",
                        columnNames = {"tenant_id", "application_id"})
        },
        indexes = {
                @Index(name = "ix_loan_account_customer", columnList = "tenant_id, customer_id"),
                @Index(name = "ix_loan_account_status", columnList = "tenant_id, status"),
                @Index(name = "ix_loan_account_arrears", columnList = "tenant_id, days_past_due")
        })
@Filter(name = TenantAwareEntity.FILTER, condition = TenantAwareEntity.CONDITION)
public class LoanAccount extends TenantAwareEntity {

    @Column(name = "account_number", nullable = false, length = 32, updatable = false)
    private String accountNumber;

    @Column(name = "application_id", nullable = false, columnDefinition = "CHAR(36)", updatable = false)
    private UUID applicationId;

    @Column(name = "customer_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID customerId;

    @Column(name = "product_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID productId;

    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    private String currency;

    @Column(name = "currency_scale", nullable = false)
    private int currencyScale = 2;

    // ---- Contracted amounts, fixed at disbursement ----

    @Column(name = "principal", nullable = false, precision = 19, scale = 4)
    private BigDecimal principal;

    @Column(name = "total_interest", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalInterest = BigDecimal.ZERO;

    @Column(name = "total_fees", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalFees = BigDecimal.ZERO;

    @Column(name = "total_repayable", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalRepayable = BigDecimal.ZERO;

    @Column(name = "installment_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal installmentAmount = BigDecimal.ZERO;

    @Column(name = "annual_interest_rate", nullable = false, precision = 9, scale = 4)
    private BigDecimal annualInterestRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_method", nullable = false, length = 24)
    private InterestMethod interestMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_frequency", nullable = false, length = 16)
    private RepaymentFrequency repaymentFrequency;

    @Column(name = "number_of_installments", nullable = false)
    private int numberOfInstallments;

    // ---- Running balances ----

    @Column(name = "outstanding_principal", nullable = false, precision = 19, scale = 4)
    private BigDecimal outstandingPrincipal = BigDecimal.ZERO;

    @Column(name = "outstanding_interest", nullable = false, precision = 19, scale = 4)
    private BigDecimal outstandingInterest = BigDecimal.ZERO;

    @Column(name = "outstanding_fees", nullable = false, precision = 19, scale = 4)
    private BigDecimal outstandingFees = BigDecimal.ZERO;

    @Column(name = "outstanding_penalty", nullable = false, precision = 19, scale = 4)
    private BigDecimal outstandingPenalty = BigDecimal.ZERO;

    @Column(name = "principal_paid", nullable = false, precision = 19, scale = 4)
    private BigDecimal principalPaid = BigDecimal.ZERO;

    @Column(name = "interest_paid", nullable = false, precision = 19, scale = 4)
    private BigDecimal interestPaid = BigDecimal.ZERO;

    @Column(name = "fees_paid", nullable = false, precision = 19, scale = 4)
    private BigDecimal feesPaid = BigDecimal.ZERO;

    @Column(name = "penalty_paid", nullable = false, precision = 19, scale = 4)
    private BigDecimal penaltyPaid = BigDecimal.ZERO;

    /** Money received beyond everything currently owed; held against future installments. */
    @Column(name = "advance_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal advanceBalance = BigDecimal.ZERO;

    // ---- Dates and arrears ----

    @Column(name = "disbursement_date", nullable = false)
    private LocalDate disbursementDate;

    @Column(name = "first_repayment_date", nullable = false)
    private LocalDate firstRepaymentDate;

    @Column(name = "maturity_date", nullable = false)
    private LocalDate maturityDate;

    @Column(name = "last_payment_date")
    private LocalDate lastPaymentDate;

    @Column(name = "days_past_due", nullable = false)
    private int daysPastDue;

    @Column(name = "overdue_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal overdueAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private LoanAccountStatus status = LoanAccountStatus.ACTIVE;

    @Column(name = "closed_on")
    private LocalDate closedOn;

    @Column(name = "branch_id", columnDefinition = "CHAR(36)")
    private UUID branchId;

    @Column(name = "loan_officer_id", columnDefinition = "CHAR(36)")
    private UUID loanOfficerId;

    protected LoanAccount() {
    }

    public LoanAccount(String accountNumber, UUID applicationId, UUID customerId, UUID productId,
                       String currency, int currencyScale, BigDecimal principal) {
        this.accountNumber = accountNumber;
        this.applicationId = applicationId;
        this.customerId = customerId;
        this.productId = productId;
        this.currency = currency;
        this.currencyScale = currencyScale;
        this.principal = principal;
        this.outstandingPrincipal = principal;
    }

    public void setContract(BigDecimal totalInterest, BigDecimal totalFees, BigDecimal totalRepayable,
                            BigDecimal installmentAmount, BigDecimal annualInterestRate,
                            InterestMethod interestMethod, RepaymentFrequency frequency,
                            int numberOfInstallments) {
        this.totalInterest = totalInterest;
        this.totalFees = totalFees;
        this.totalRepayable = totalRepayable;
        this.installmentAmount = installmentAmount;
        this.annualInterestRate = annualInterestRate;
        this.interestMethod = interestMethod;
        this.repaymentFrequency = frequency;
        this.numberOfInstallments = numberOfInstallments;
        this.outstandingInterest = totalInterest;
        this.outstandingFees = totalFees;
    }

    public void setDates(LocalDate disbursementDate, LocalDate firstRepaymentDate,
                         LocalDate maturityDate) {
        this.disbursementDate = disbursementDate;
        this.firstRepaymentDate = firstRepaymentDate;
        this.maturityDate = maturityDate;
    }

    public void assign(UUID branchId, UUID loanOfficerId) {
        this.branchId = branchId;
        this.loanOfficerId = loanOfficerId;
    }

    /** Everything the borrower still owes, on and off schedule. */
    public BigDecimal totalOutstanding() {
        return outstandingPrincipal
                .add(outstandingInterest)
                .add(outstandingFees)
                .add(outstandingPenalty)
                .subtract(advanceBalance);
    }

    /**
     * Moves the running balances by an allocation that has already been apportioned across the
     * schedule. Called only by the repayment service, inside the same transaction as the
     * schedule updates, so the two can never disagree.
     */
    public void applyAllocation(BigDecimal principalPart, BigDecimal interestPart,
                                BigDecimal feePart, BigDecimal penaltyPart,
                                BigDecimal advancePart, LocalDate valueDate) {
        this.outstandingPrincipal = subtractToZero(outstandingPrincipal, principalPart);
        this.outstandingInterest = subtractToZero(outstandingInterest, interestPart);
        this.outstandingFees = subtractToZero(outstandingFees, feePart);
        this.outstandingPenalty = subtractToZero(outstandingPenalty, penaltyPart);

        this.principalPaid = principalPaid.add(principalPart);
        this.interestPaid = interestPaid.add(interestPart);
        this.feesPaid = feesPaid.add(feePart);
        this.penaltyPaid = penaltyPaid.add(penaltyPart);
        this.advanceBalance = advanceBalance.add(advancePart);
        this.lastPaymentDate = valueDate;

        if (isFullyRepaid()) {
            close(valueDate);
        }
    }

    /** Reverses a previously applied allocation, restoring the balances exactly. */
    public void reverseAllocation(BigDecimal principalPart, BigDecimal interestPart,
                                  BigDecimal feePart, BigDecimal penaltyPart,
                                  BigDecimal advancePart) {
        this.outstandingPrincipal = outstandingPrincipal.add(principalPart);
        this.outstandingInterest = outstandingInterest.add(interestPart);
        this.outstandingFees = outstandingFees.add(feePart);
        this.outstandingPenalty = outstandingPenalty.add(penaltyPart);

        this.principalPaid = subtractToZero(principalPaid, principalPart);
        this.interestPaid = subtractToZero(interestPaid, interestPart);
        this.feesPaid = subtractToZero(feesPaid, feePart);
        this.penaltyPaid = subtractToZero(penaltyPaid, penaltyPart);
        this.advanceBalance = subtractToZero(advanceBalance, advancePart);

        if (status == LoanAccountStatus.CLOSED) {
            // A reversal after settlement re-opens the loan; it is no longer paid off.
            this.status = LoanAccountStatus.ACTIVE;
            this.closedOn = null;
        }
    }

    public boolean isFullyRepaid() {
        return !Money.isPositive(outstandingPrincipal)
                && !Money.isPositive(outstandingInterest)
                && !Money.isPositive(outstandingFees)
                && !Money.isPositive(outstandingPenalty);
    }

    public void requireOpen() {
        if (status != LoanAccountStatus.ACTIVE && status != LoanAccountStatus.OVERDUE) {
            throw new ApiExceptions.BusinessRuleException(
                    "Loan " + accountNumber + " is " + status + " and cannot accept transactions");
        }
    }

    public void close(LocalDate closedOn) {
        this.status = LoanAccountStatus.CLOSED;
        this.closedOn = closedOn;
        this.daysPastDue = 0;
        this.overdueAmount = BigDecimal.ZERO;
    }

    /** Updates the arrears position; called by the daily arrears job. */
    public void updateArrears(int daysPastDue, BigDecimal overdueAmount, int daysToDefault) {
        this.daysPastDue = daysPastDue;
        this.overdueAmount = overdueAmount;
        if (status == LoanAccountStatus.CLOSED || status == LoanAccountStatus.WRITTEN_OFF) {
            return;
        }
        if (daysPastDue >= daysToDefault) {
            this.status = LoanAccountStatus.DEFAULTED;
        } else if (daysPastDue > 0) {
            this.status = LoanAccountStatus.OVERDUE;
        } else {
            this.status = LoanAccountStatus.ACTIVE;
        }
    }

    public void accruePenalty(BigDecimal amount) {
        this.outstandingPenalty = outstandingPenalty.add(amount);
    }

    public void writeOff() {
        this.status = LoanAccountStatus.WRITTEN_OFF;
    }

    private BigDecimal subtractToZero(BigDecimal balance, BigDecimal amount) {
        BigDecimal result = balance.subtract(amount == null ? BigDecimal.ZERO : amount);
        return Money.isNegative(result) ? BigDecimal.ZERO.setScale(result.scale()) : result;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getCurrency() {
        return currency;
    }

    public int getCurrencyScale() {
        return currencyScale;
    }

    public BigDecimal getPrincipal() {
        return principal;
    }

    public BigDecimal getTotalInterest() {
        return totalInterest;
    }

    public BigDecimal getTotalFees() {
        return totalFees;
    }

    public BigDecimal getTotalRepayable() {
        return totalRepayable;
    }

    public BigDecimal getInstallmentAmount() {
        return installmentAmount;
    }

    public BigDecimal getAnnualInterestRate() {
        return annualInterestRate;
    }

    public InterestMethod getInterestMethod() {
        return interestMethod;
    }

    public RepaymentFrequency getRepaymentFrequency() {
        return repaymentFrequency;
    }

    public int getNumberOfInstallments() {
        return numberOfInstallments;
    }

    public BigDecimal getOutstandingPrincipal() {
        return outstandingPrincipal;
    }

    public BigDecimal getOutstandingInterest() {
        return outstandingInterest;
    }

    public BigDecimal getOutstandingFees() {
        return outstandingFees;
    }

    public BigDecimal getOutstandingPenalty() {
        return outstandingPenalty;
    }

    public BigDecimal getPrincipalPaid() {
        return principalPaid;
    }

    public BigDecimal getInterestPaid() {
        return interestPaid;
    }

    public BigDecimal getFeesPaid() {
        return feesPaid;
    }

    public BigDecimal getPenaltyPaid() {
        return penaltyPaid;
    }

    public BigDecimal getAdvanceBalance() {
        return advanceBalance;
    }

    public LocalDate getDisbursementDate() {
        return disbursementDate;
    }

    public LocalDate getFirstRepaymentDate() {
        return firstRepaymentDate;
    }

    public LocalDate getMaturityDate() {
        return maturityDate;
    }

    public LocalDate getLastPaymentDate() {
        return lastPaymentDate;
    }

    public int getDaysPastDue() {
        return daysPastDue;
    }

    public BigDecimal getOverdueAmount() {
        return overdueAmount;
    }

    public LoanAccountStatus getStatus() {
        return status;
    }

    public LocalDate getClosedOn() {
        return closedOn;
    }

    public UUID getBranchId() {
        return branchId;
    }

    public UUID getLoanOfficerId() {
        return loanOfficerId;
    }
}

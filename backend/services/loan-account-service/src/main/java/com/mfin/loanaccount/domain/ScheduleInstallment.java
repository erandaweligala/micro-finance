package com.mfin.loanaccount.domain;

import com.mfin.common.persistence.TenantAwareEntity;
import com.mfin.loan.engine.Money;
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
 * One installment of a live loan's repayment schedule.
 *
 * <p>Each bucket carries both what is due and what has been paid, so the arrears position of a
 * loan can be reconstructed for any date - which is what a customer statement, an
 * impairment calculation and a regulator's aged-debt report all need.</p>
 */
@Entity
@Table(name = "schedule_installment",
        uniqueConstraints = @UniqueConstraint(name = "ux_installment_account_number",
                columnNames = {"loan_account_id", "installment_number"}),
        indexes = {
                @Index(name = "ix_installment_account", columnList = "tenant_id, loan_account_id"),
                @Index(name = "ix_installment_due", columnList = "tenant_id, due_date, status")
        })
@Filter(name = TenantAwareEntity.FILTER, condition = TenantAwareEntity.CONDITION)
public class ScheduleInstallment extends TenantAwareEntity {

    @Column(name = "loan_account_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID loanAccountId;

    @Column(name = "installment_number", nullable = false)
    private int installmentNumber;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "opening_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @Column(name = "principal_due", nullable = false, precision = 19, scale = 4)
    private BigDecimal principalDue = BigDecimal.ZERO;

    @Column(name = "interest_due", nullable = false, precision = 19, scale = 4)
    private BigDecimal interestDue = BigDecimal.ZERO;

    @Column(name = "fee_due", nullable = false, precision = 19, scale = 4)
    private BigDecimal feeDue = BigDecimal.ZERO;

    /** Accrues after the due date; not part of the original contract. */
    @Column(name = "penalty_due", nullable = false, precision = 19, scale = 4)
    private BigDecimal penaltyDue = BigDecimal.ZERO;

    @Column(name = "principal_paid", nullable = false, precision = 19, scale = 4)
    private BigDecimal principalPaid = BigDecimal.ZERO;

    @Column(name = "interest_paid", nullable = false, precision = 19, scale = 4)
    private BigDecimal interestPaid = BigDecimal.ZERO;

    @Column(name = "fee_paid", nullable = false, precision = 19, scale = 4)
    private BigDecimal feePaid = BigDecimal.ZERO;

    @Column(name = "penalty_paid", nullable = false, precision = 19, scale = 4)
    private BigDecimal penaltyPaid = BigDecimal.ZERO;

    @Column(name = "closing_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal closingBalance = BigDecimal.ZERO;

    @Column(name = "grace_installment", nullable = false)
    private boolean graceInstallment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private InstallmentStatus status = InstallmentStatus.PENDING;

    @Column(name = "settled_on")
    private LocalDate settledOn;

    protected ScheduleInstallment() {
    }

    public ScheduleInstallment(UUID loanAccountId, int installmentNumber, LocalDate dueDate) {
        this.loanAccountId = loanAccountId;
        this.installmentNumber = installmentNumber;
        this.dueDate = dueDate;
    }

    public void setAmounts(BigDecimal openingBalance, BigDecimal principalDue, BigDecimal interestDue,
                           BigDecimal feeDue, BigDecimal closingBalance, boolean graceInstallment) {
        this.openingBalance = openingBalance;
        this.principalDue = principalDue;
        this.interestDue = interestDue;
        this.feeDue = feeDue;
        this.closingBalance = closingBalance;
        this.graceInstallment = graceInstallment;
    }

    public BigDecimal totalDue() {
        return principalDue.add(interestDue).add(feeDue).add(penaltyDue);
    }

    public BigDecimal totalPaid() {
        return principalPaid.add(interestPaid).add(feePaid).add(penaltyPaid);
    }

    public BigDecimal outstandingPrincipal() {
        return principalDue.subtract(principalPaid);
    }

    public BigDecimal outstandingInterest() {
        return interestDue.subtract(interestPaid);
    }

    public BigDecimal outstandingFee() {
        return feeDue.subtract(feePaid);
    }

    public BigDecimal outstandingPenalty() {
        return penaltyDue.subtract(penaltyPaid);
    }

    public BigDecimal totalOutstanding() {
        return totalDue().subtract(totalPaid());
    }

    public boolean isSettled() {
        return !Money.isPositive(totalOutstanding());
    }

    /** Records money received against this installment's buckets. */
    public void allocate(BigDecimal principal, BigDecimal interest, BigDecimal fee,
                         BigDecimal penalty, LocalDate valueDate) {
        this.principalPaid = principalPaid.add(principal);
        this.interestPaid = interestPaid.add(interest);
        this.feePaid = feePaid.add(fee);
        this.penaltyPaid = penaltyPaid.add(penalty);
        refreshStatus(valueDate);
    }

    public void reverseAllocation(BigDecimal principal, BigDecimal interest, BigDecimal fee,
                                  BigDecimal penalty, LocalDate today) {
        this.principalPaid = principalPaid.subtract(principal);
        this.interestPaid = interestPaid.subtract(interest);
        this.feePaid = feePaid.subtract(fee);
        this.penaltyPaid = penaltyPaid.subtract(penalty);
        this.settledOn = null;
        refreshStatus(today);
    }

    public void addPenalty(BigDecimal amount) {
        this.penaltyDue = penaltyDue.add(amount);
        if (status == InstallmentStatus.PAID) {
            status = InstallmentStatus.PARTIALLY_PAID;
        }
    }

    /** Recomputes the installment's status from its balances and the date it is judged on. */
    public void refreshStatus(LocalDate asOf) {
        if (isSettled()) {
            this.status = InstallmentStatus.PAID;
            if (this.settledOn == null) {
                this.settledOn = asOf;
            }
            return;
        }
        boolean anythingPaid = Money.isPositive(totalPaid());
        if (dueDate.isBefore(asOf)) {
            this.status = InstallmentStatus.OVERDUE;
        } else {
            this.status = anythingPaid ? InstallmentStatus.PARTIALLY_PAID : InstallmentStatus.PENDING;
        }
    }

    public boolean isDueBy(LocalDate date) {
        return !dueDate.isAfter(date);
    }

    public UUID getLoanAccountId() {
        return loanAccountId;
    }

    public int getInstallmentNumber() {
        return installmentNumber;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public BigDecimal getPrincipalDue() {
        return principalDue;
    }

    public BigDecimal getInterestDue() {
        return interestDue;
    }

    public BigDecimal getFeeDue() {
        return feeDue;
    }

    public BigDecimal getPenaltyDue() {
        return penaltyDue;
    }

    public BigDecimal getPrincipalPaid() {
        return principalPaid;
    }

    public BigDecimal getInterestPaid() {
        return interestPaid;
    }

    public BigDecimal getFeePaid() {
        return feePaid;
    }

    public BigDecimal getPenaltyPaid() {
        return penaltyPaid;
    }

    public BigDecimal getClosingBalance() {
        return closingBalance;
    }

    public boolean isGraceInstallment() {
        return graceInstallment;
    }

    public InstallmentStatus getStatus() {
        return status;
    }

    public LocalDate getSettledOn() {
        return settledOn;
    }
}

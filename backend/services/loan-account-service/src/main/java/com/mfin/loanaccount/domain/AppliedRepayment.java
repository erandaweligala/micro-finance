package com.mfin.loanaccount.domain;

import com.mfin.common.persistence.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The record of a payment having been applied to a loan, with the split it produced.
 *
 * <p>Serves two purposes. It makes repayment posting idempotent - the unique constraint on
 * {@code (tenant_id, payment_id)} means a retried call finds the existing row instead of moving
 * the balances again. And it preserves the original allocation so a reversal can be replayed
 * exactly, rather than recomputed against balances that have since moved.</p>
 */
@Entity
@Table(name = "applied_repayment",
        uniqueConstraints = @UniqueConstraint(name = "ux_applied_repayment_payment",
                columnNames = {"tenant_id", "payment_id"}),
        indexes = @Index(name = "ix_applied_repayment_account", columnList = "tenant_id, loan_account_id"))
@Filter(name = TenantAwareEntity.FILTER, condition = TenantAwareEntity.CONDITION)
public class AppliedRepayment extends TenantAwareEntity {

    @Column(name = "loan_account_id", nullable = false, columnDefinition = "CHAR(36)", updatable = false)
    private UUID loanAccountId;

    @Column(name = "payment_id", nullable = false, columnDefinition = "CHAR(36)", updatable = false)
    private UUID paymentId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(name = "principal_allocated", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal principalAllocated;

    @Column(name = "interest_allocated", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal interestAllocated;

    @Column(name = "fee_allocated", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal feeAllocated;

    @Column(name = "penalty_allocated", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal penaltyAllocated;

    /** Money that exceeded everything owed and became a credit balance. */
    @Column(name = "excess_amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal excessAmount;

    @Column(name = "value_date", nullable = false, updatable = false)
    private LocalDate valueDate;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "reversed_by", columnDefinition = "CHAR(36)")
    private UUID reversedBy;

    @Column(name = "reversal_reason", length = 512)
    private String reversalReason;

    protected AppliedRepayment() {
    }

    public AppliedRepayment(UUID loanAccountId, UUID paymentId, BigDecimal amount,
                            BigDecimal principalAllocated, BigDecimal interestAllocated,
                            BigDecimal feeAllocated, BigDecimal penaltyAllocated,
                            BigDecimal excessAmount, LocalDate valueDate) {
        this.loanAccountId = loanAccountId;
        this.paymentId = paymentId;
        this.amount = amount;
        this.principalAllocated = principalAllocated;
        this.interestAllocated = interestAllocated;
        this.feeAllocated = feeAllocated;
        this.penaltyAllocated = penaltyAllocated;
        this.excessAmount = excessAmount;
        this.valueDate = valueDate;
    }

    public boolean isReversed() {
        return reversedAt != null;
    }

    public void markReversed(UUID reversedBy, String reason) {
        this.reversedAt = Instant.now();
        this.reversedBy = reversedBy;
        this.reversalReason = reason;
    }

    public UUID getLoanAccountId() {
        return loanAccountId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getPrincipalAllocated() {
        return principalAllocated;
    }

    public BigDecimal getInterestAllocated() {
        return interestAllocated;
    }

    public BigDecimal getFeeAllocated() {
        return feeAllocated;
    }

    public BigDecimal getPenaltyAllocated() {
        return penaltyAllocated;
    }

    public BigDecimal getExcessAmount() {
        return excessAmount;
    }

    public LocalDate getValueDate() {
        return valueDate;
    }

    public Instant getReversedAt() {
        return reversedAt;
    }

    public UUID getReversedBy() {
        return reversedBy;
    }

    public String getReversalReason() {
        return reversalReason;
    }
}

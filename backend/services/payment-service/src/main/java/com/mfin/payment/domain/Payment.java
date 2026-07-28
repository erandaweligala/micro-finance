package com.mfin.payment.domain;

import com.mfin.common.error.ApiExceptions;
import com.mfin.common.persistence.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A payment received from a borrower.
 *
 * <p>Payments are never edited or deleted. A mistake is corrected by a <em>reversal</em>, which
 * leaves both the original and the correction on the record - the same discipline a cash book
 * or a general ledger follows, and what an auditor expects to find.</p>
 *
 * <p>The allocation columns are a snapshot of how the loan account service split this payment.
 * They are stored so a receipt can be reprinted years later without another service call.</p>
 */
@Entity
@Table(name = "payment",
        uniqueConstraints = {
                @UniqueConstraint(name = "ux_payment_tenant_receipt",
                        columnNames = {"tenant_id", "receipt_number"})
        },
        indexes = {
                @Index(name = "ix_payment_loan", columnList = "tenant_id, loan_account_id"),
                @Index(name = "ix_payment_customer", columnList = "tenant_id, customer_id"),
                @Index(name = "ix_payment_value_date", columnList = "tenant_id, value_date"),
                @Index(name = "ix_payment_status", columnList = "tenant_id, status")
        })
@Filter(name = TenantAwareEntity.FILTER, condition = TenantAwareEntity.CONDITION)
public class Payment extends TenantAwareEntity {

    @Column(name = "receipt_number", nullable = false, length = 32, updatable = false)
    private String receiptNumber;

    @Column(name = "loan_account_id", nullable = false, columnDefinition = "CHAR(36)", updatable = false)
    private UUID loanAccountId;

    @Column(name = "loan_account_number", length = 32)
    private String loanAccountNumber;

    @Column(name = "customer_id", nullable = false, columnDefinition = "CHAR(36)", updatable = false)
    private UUID customerId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 32, updatable = false)
    private PaymentMethod method;

    /** Bank/mobile-money transaction id or cash receipt reference from the channel. */
    @Column(name = "external_reference", length = 64)
    private String externalReference;

    @Column(name = "value_date", nullable = false, updatable = false)
    private LocalDate valueDate;

    @Column(name = "narrative", length = 512)
    private String narrative;

    // ---- Allocation snapshot, as applied by the loan account service ----

    @Column(name = "principal_allocated", nullable = false, precision = 19, scale = 4)
    private BigDecimal principalAllocated = BigDecimal.ZERO;

    @Column(name = "interest_allocated", nullable = false, precision = 19, scale = 4)
    private BigDecimal interestAllocated = BigDecimal.ZERO;

    @Column(name = "fee_allocated", nullable = false, precision = 19, scale = 4)
    private BigDecimal feeAllocated = BigDecimal.ZERO;

    @Column(name = "penalty_allocated", nullable = false, precision = 19, scale = 4)
    private BigDecimal penaltyAllocated = BigDecimal.ZERO;

    @Column(name = "excess_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal excessAmount = BigDecimal.ZERO;

    @Column(name = "outstanding_principal_after", precision = 19, scale = 4)
    private BigDecimal outstandingPrincipalAfter;

    @Column(name = "total_outstanding_after", precision = 19, scale = 4)
    private BigDecimal totalOutstandingAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private PaymentStatus status = PaymentStatus.POSTED;

    @Column(name = "received_by", columnDefinition = "CHAR(36)")
    private UUID receivedBy;

    @Column(name = "branch_id", columnDefinition = "CHAR(36)")
    private UUID branchId;

    // ---- Reversal ----

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "reversed_by", columnDefinition = "CHAR(36)")
    private UUID reversedBy;

    @Column(name = "reversal_reason", length = 512)
    private String reversalReason;

    protected Payment() {
    }

    public Payment(String receiptNumber, UUID loanAccountId, UUID customerId, BigDecimal amount,
                   String currency, PaymentMethod method, LocalDate valueDate) {
        this.receiptNumber = receiptNumber;
        this.loanAccountId = loanAccountId;
        this.customerId = customerId;
        this.amount = amount;
        this.currency = currency;
        this.method = method;
        this.valueDate = valueDate;
    }

    public void recordAllocation(BigDecimal principal, BigDecimal interest, BigDecimal fee,
                                 BigDecimal penalty, BigDecimal excess,
                                 BigDecimal outstandingPrincipalAfter,
                                 BigDecimal totalOutstandingAfter, String loanAccountNumber) {
        this.principalAllocated = principal;
        this.interestAllocated = interest;
        this.feeAllocated = fee;
        this.penaltyAllocated = penalty;
        this.excessAmount = excess;
        this.outstandingPrincipalAfter = outstandingPrincipalAfter;
        this.totalOutstandingAfter = totalOutstandingAfter;
        this.loanAccountNumber = loanAccountNumber;
    }

    public void describe(String externalReference, String narrative, UUID receivedBy, UUID branchId) {
        this.externalReference = externalReference;
        this.narrative = narrative;
        this.receivedBy = receivedBy;
        this.branchId = branchId;
    }

    public void reverse(UUID reversedBy, String reason) {
        if (status == PaymentStatus.REVERSED) {
            throw new ApiExceptions.BusinessRuleException(
                    "Receipt " + receiptNumber + " has already been reversed");
        }
        this.status = PaymentStatus.REVERSED;
        this.reversedAt = Instant.now();
        this.reversedBy = reversedBy;
        this.reversalReason = reason;
    }

    public boolean isReversed() {
        return status == PaymentStatus.REVERSED;
    }

    public String getReceiptNumber() {
        return receiptNumber;
    }

    public UUID getLoanAccountId() {
        return loanAccountId;
    }

    public String getLoanAccountNumber() {
        return loanAccountNumber;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public LocalDate getValueDate() {
        return valueDate;
    }

    public String getNarrative() {
        return narrative;
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

    public BigDecimal getOutstandingPrincipalAfter() {
        return outstandingPrincipalAfter;
    }

    public BigDecimal getTotalOutstandingAfter() {
        return totalOutstandingAfter;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public UUID getReceivedBy() {
        return receivedBy;
    }

    public UUID getBranchId() {
        return branchId;
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

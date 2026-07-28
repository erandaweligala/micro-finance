package com.mfin.ledger.domain;

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
 * One line of a customer's loan ledger.
 *
 * <p><strong>Append-only.</strong> There is no update path and no delete path: a correction is
 * a new, opposite entry that references the original. That is what makes the ledger admissible
 * as a record - any row can be traced to the event that produced it, and the balance on any
 * past date can be reproduced by replaying entries up to that date.</p>
 *
 * <p>Each row carries the running {@code outstandingPrincipal} and {@code totalOutstanding} as
 * at that transaction, so a statement needs no window functions and no recomputation.</p>
 */
@Entity
@Table(name = "ledger_entry",
        uniqueConstraints = {
                // The idempotency guard for at-least-once event delivery: one entry per source event.
                @UniqueConstraint(name = "ux_ledger_entry_source_event",
                        columnNames = {"tenant_id", "source_event_id"})
        },
        indexes = {
                @Index(name = "ix_ledger_loan_date", columnList = "tenant_id, loan_account_id, transaction_date"),
                @Index(name = "ix_ledger_customer", columnList = "tenant_id, customer_id"),
                @Index(name = "ix_ledger_reference", columnList = "tenant_id, transaction_reference")
        })
@Filter(name = TenantAwareEntity.FILTER, condition = TenantAwareEntity.CONDITION)
public class LedgerEntry extends TenantAwareEntity {

    @Column(name = "loan_account_id", nullable = false, columnDefinition = "CHAR(36)", updatable = false)
    private UUID loanAccountId;

    @Column(name = "loan_account_number", length = 32, updatable = false)
    private String loanAccountNumber;

    @Column(name = "customer_id", nullable = false, columnDefinition = "CHAR(36)", updatable = false)
    private UUID customerId;

    @Column(name = "transaction_date", nullable = false, updatable = false)
    private LocalDate transactionDate;

    /** Receipt number, disbursement reference, or the system reference for an accrual. */
    @Column(name = "transaction_reference", nullable = false, length = 64, updatable = false)
    private String transactionReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 32, updatable = false)
    private LedgerTransactionType transactionType;

    @Column(name = "narrative", length = 512, updatable = false)
    private String narrative;

    /**
     * Increases what the customer owes: a disbursement, an interest or penalty charge, or the
     * contra entry of a reversed repayment.
     */
    @Column(name = "debit_amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal debitAmount = BigDecimal.ZERO;

    /** Reduces what the customer owes: a repayment, a waiver, or a write-off. */
    @Column(name = "credit_amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal creditAmount = BigDecimal.ZERO;

    @Column(name = "principal_allocation", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal principalAllocation = BigDecimal.ZERO;

    @Column(name = "interest_allocation", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal interestAllocation = BigDecimal.ZERO;

    @Column(name = "fee_allocation", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal feeAllocation = BigDecimal.ZERO;

    @Column(name = "penalty_allocation", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal penaltyAllocation = BigDecimal.ZERO;

    @Column(name = "outstanding_principal", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal outstandingPrincipal = BigDecimal.ZERO;

    @Column(name = "total_outstanding", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal totalOutstanding = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency;

    /** The domain event that produced this line; makes replay idempotent and auditable. */
    @Column(name = "source_event_id", nullable = false, columnDefinition = "CHAR(36)", updatable = false)
    private UUID sourceEventId;

    @Column(name = "source_event_type", nullable = false, length = 96, updatable = false)
    private String sourceEventType;

    /** Set on a contra entry to point at the line it reverses. */
    @Column(name = "reverses_entry_id", columnDefinition = "CHAR(36)", updatable = false)
    private UUID reversesEntryId;

    @Column(name = "posted_at", nullable = false, updatable = false)
    private Instant postedAt = Instant.now();

    protected LedgerEntry() {
    }

    public LedgerEntry(UUID loanAccountId, UUID customerId, LocalDate transactionDate,
                       String transactionReference, LedgerTransactionType transactionType,
                       String currency, UUID sourceEventId, String sourceEventType) {
        this.loanAccountId = loanAccountId;
        this.customerId = customerId;
        this.transactionDate = transactionDate;
        this.transactionReference = transactionReference;
        this.transactionType = transactionType;
        this.currency = currency;
        this.sourceEventId = sourceEventId;
        this.sourceEventType = sourceEventType;
    }

    public LedgerEntry debit(BigDecimal amount) {
        this.debitAmount = amount;
        return this;
    }

    public LedgerEntry credit(BigDecimal amount) {
        this.creditAmount = amount;
        return this;
    }

    public LedgerEntry withAllocation(BigDecimal principal, BigDecimal interest, BigDecimal fee,
                                      BigDecimal penalty) {
        this.principalAllocation = nullToZero(principal);
        this.interestAllocation = nullToZero(interest);
        this.feeAllocation = nullToZero(fee);
        this.penaltyAllocation = nullToZero(penalty);
        return this;
    }

    public LedgerEntry withBalances(BigDecimal outstandingPrincipal, BigDecimal totalOutstanding) {
        this.outstandingPrincipal = nullToZero(outstandingPrincipal);
        this.totalOutstanding = nullToZero(totalOutstanding);
        return this;
    }

    public LedgerEntry withNarrative(String narrative) {
        this.narrative = narrative;
        return this;
    }

    public LedgerEntry withAccountNumber(String loanAccountNumber) {
        this.loanAccountNumber = loanAccountNumber;
        return this;
    }

    public LedgerEntry reversing(UUID originalEntryId) {
        this.reversesEntryId = originalEntryId;
        return this;
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
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

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public LedgerTransactionType getTransactionType() {
        return transactionType;
    }

    public String getNarrative() {
        return narrative;
    }

    public BigDecimal getDebitAmount() {
        return debitAmount;
    }

    public BigDecimal getCreditAmount() {
        return creditAmount;
    }

    public BigDecimal getPrincipalAllocation() {
        return principalAllocation;
    }

    public BigDecimal getInterestAllocation() {
        return interestAllocation;
    }

    public BigDecimal getFeeAllocation() {
        return feeAllocation;
    }

    public BigDecimal getPenaltyAllocation() {
        return penaltyAllocation;
    }

    public BigDecimal getOutstandingPrincipal() {
        return outstandingPrincipal;
    }

    public BigDecimal getTotalOutstanding() {
        return totalOutstanding;
    }

    public String getCurrency() {
        return currency;
    }

    public UUID getSourceEventId() {
        return sourceEventId;
    }

    public String getSourceEventType() {
        return sourceEventType;
    }

    public UUID getReversesEntryId() {
        return reversesEntryId;
    }

    public Instant getPostedAt() {
        return postedAt;
    }
}

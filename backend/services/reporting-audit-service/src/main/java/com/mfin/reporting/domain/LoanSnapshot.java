package com.mfin.reporting.domain;

import com.mfin.common.persistence.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A denormalised view of one loan, maintained from events.
 *
 * <p>Reporting keeps its own read model rather than querying the loan account service: portfolio
 * reports scan the whole book, and running those scans against the transactional database would
 * put analytics load on the system that has to accept repayments.</p>
 */
@Entity
@Table(name = "loan_snapshot",
        uniqueConstraints = @UniqueConstraint(name = "ux_snapshot_loan",
                columnNames = {"tenant_id", "loan_account_id"}),
        indexes = {
                @Index(name = "ix_snapshot_status", columnList = "tenant_id, status"),
                @Index(name = "ix_snapshot_arrears", columnList = "tenant_id, days_past_due"),
                @Index(name = "ix_snapshot_branch", columnList = "tenant_id, branch_id")
        })
@Filter(name = TenantAwareEntity.FILTER, condition = TenantAwareEntity.CONDITION)
public class LoanSnapshot extends TenantAwareEntity {

    @Column(name = "loan_account_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID loanAccountId;

    @Column(name = "account_number", length = 32)
    private String accountNumber;

    @Column(name = "customer_id", columnDefinition = "CHAR(36)")
    private UUID customerId;

    @Column(name = "branch_id", columnDefinition = "CHAR(36)")
    private UUID branchId;

    @Column(name = "principal", nullable = false, precision = 19, scale = 4)
    private BigDecimal principal = BigDecimal.ZERO;

    @Column(name = "outstanding_principal", nullable = false, precision = 19, scale = 4)
    private BigDecimal outstandingPrincipal = BigDecimal.ZERO;

    @Column(name = "total_outstanding", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalOutstanding = BigDecimal.ZERO;

    @Column(name = "total_collected", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalCollected = BigDecimal.ZERO;

    @Column(name = "days_past_due", nullable = false)
    private int daysPastDue;

    @Column(name = "status", nullable = false, length = 24)
    private String status = "ACTIVE";

    @Column(name = "disbursement_date")
    private LocalDate disbursementDate;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @Column(name = "last_payment_date")
    private LocalDate lastPaymentDate;

    protected LoanSnapshot() {
    }

    public LoanSnapshot(UUID loanAccountId, String accountNumber, UUID customerId) {
        this.loanAccountId = loanAccountId;
        this.accountNumber = accountNumber;
        this.customerId = customerId;
    }

    public void onDisbursed(BigDecimal principal, BigDecimal totalRepayable,
                            LocalDate disbursementDate, LocalDate maturityDate) {
        this.principal = principal;
        this.outstandingPrincipal = principal;
        this.totalOutstanding = totalRepayable;
        this.disbursementDate = disbursementDate;
        this.maturityDate = maturityDate;
    }

    public void onPayment(BigDecimal amount, BigDecimal outstandingPrincipal,
                          BigDecimal totalOutstanding, LocalDate valueDate) {
        this.totalCollected = this.totalCollected.add(amount);
        this.outstandingPrincipal = outstandingPrincipal;
        this.totalOutstanding = totalOutstanding;
        this.lastPaymentDate = valueDate;
        if (totalOutstanding != null && totalOutstanding.signum() <= 0) {
            this.status = "CLOSED";
            this.daysPastDue = 0;
        }
    }

    public void onOverdue(int daysPastDue) {
        this.daysPastDue = daysPastDue;
        this.status = daysPastDue > 0 ? "OVERDUE" : this.status;
    }

    /** Standard portfolio-at-risk buckets used in microfinance reporting. */
    public String arrearsBucket() {
        if (daysPastDue <= 0) {
            return "CURRENT";
        }
        if (daysPastDue <= 30) {
            return "PAR_1_30";
        }
        if (daysPastDue <= 60) {
            return "PAR_31_60";
        }
        if (daysPastDue <= 90) {
            return "PAR_61_90";
        }
        return "PAR_90_PLUS";
    }

    public UUID getLoanAccountId() {
        return loanAccountId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getBranchId() {
        return branchId;
    }

    public BigDecimal getPrincipal() {
        return principal;
    }

    public BigDecimal getOutstandingPrincipal() {
        return outstandingPrincipal;
    }

    public BigDecimal getTotalOutstanding() {
        return totalOutstanding;
    }

    public BigDecimal getTotalCollected() {
        return totalCollected;
    }

    public int getDaysPastDue() {
        return daysPastDue;
    }

    public String getStatus() {
        return status;
    }

    public LocalDate getDisbursementDate() {
        return disbursementDate;
    }

    public LocalDate getMaturityDate() {
        return maturityDate;
    }

    public LocalDate getLastPaymentDate() {
        return lastPaymentDate;
    }
}

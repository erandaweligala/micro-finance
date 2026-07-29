package com.mfin.origination.domain;

import com.mfin.common.error.ApiExceptions;
import com.mfin.common.persistence.TenantAwareEntity;
import com.mfin.loan.engine.GraceType;
import com.mfin.loan.engine.InterestMethod;
import com.mfin.loan.engine.RepaymentFrequency;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A loan application, from capture through approval to disbursement.
 *
 * <p>Requested and approved terms are held separately: an approver may sanction less than was
 * asked for, and the difference is exactly what a lending committee needs to see afterwards.
 * The approved figures - never the requested ones - are what get disbursed.</p>
 */
@Entity
@Table(name = "loan_application",
        uniqueConstraints = @UniqueConstraint(name = "ux_application_tenant_number",
                columnNames = {"tenant_id", "application_number"}),
        indexes = {
                @Index(name = "ix_application_tenant_status", columnList = "tenant_id, status"),
                @Index(name = "ix_application_customer", columnList = "tenant_id, customer_id"),
                @Index(name = "ix_application_branch", columnList = "tenant_id, branch_id")
        })
@Filter(name = TenantAwareEntity.FILTER, condition = TenantAwareEntity.CONDITION)
public class LoanApplication extends TenantAwareEntity {

    @Column(name = "application_number", nullable = false, length = 32, updatable = false)
    private String applicationNumber;

    @Column(name = "customer_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID customerId;

    @Column(name = "customer_name", length = 160)
    private String customerName;

    @Column(name = "product_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID productId;

    @Column(name = "product_name", length = 128)
    private String productName;

    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    private String currency;

    // ---- Requested terms ----

    @Column(name = "requested_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal requestedAmount;

    @Column(name = "requested_installments", nullable = false)
    private int requestedInstallments;

    // ---- Approved terms (populated at approval) ----

    @Column(name = "approved_amount", precision = 19, scale = 4)
    private BigDecimal approvedAmount;

    @Column(name = "approved_installments")
    private Integer approvedInstallments;

    @Column(name = "annual_interest_rate", nullable = false, precision = 9, scale = 4)
    private BigDecimal annualInterestRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_method", nullable = false, length = 24)
    private InterestMethod interestMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_frequency", nullable = false, length = 16)
    private RepaymentFrequency repaymentFrequency;

    @Enumerated(EnumType.STRING)
    @Column(name = "grace_type", nullable = false, length = 24)
    private GraceType graceType = GraceType.NONE;

    @Column(name = "grace_periods", nullable = false)
    private int gracePeriods;

    @Column(name = "purpose", length = 512)
    private String purpose;

    @Column(name = "expected_disbursement_date")
    private LocalDate expectedDisbursementDate;

    @Column(name = "first_repayment_date")
    private LocalDate firstRepaymentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private LoanApplicationStatus status = LoanApplicationStatus.DRAFT;

    /** Approvals recorded so far; the loan is approved when this reaches the required level. */
    @Column(name = "current_approval_level", nullable = false)
    private int currentApprovalLevel;

    @Column(name = "required_approval_levels", nullable = false)
    private int requiredApprovalLevels = 1;

    @Column(name = "branch_id", columnDefinition = "CHAR(36)")
    private UUID branchId;

    @Column(name = "loan_officer_id", columnDefinition = "CHAR(36)")
    private UUID loanOfficerId;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "rejection_reason", length = 512)
    private String rejectionReason;

    // ---- Disbursement ----

    @Column(name = "disbursed_at")
    private Instant disbursedAt;

    @Column(name = "disbursement_date")
    private LocalDate disbursementDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "disbursement_method", length = 32)
    private DisbursementMethod disbursementMethod;

    @Column(name = "disbursement_reference", length = 64)
    private String disbursementReference;

    @Column(name = "disbursed_amount", precision = 19, scale = 4)
    private BigDecimal disbursedAmount;

    @Column(name = "net_disbursed_amount", precision = 19, scale = 4)
    private BigDecimal netDisbursedAmount;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "application_id", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_approval_application"))
    @OrderBy("createdAt asc")
    private List<ApprovalRecord> approvals = new ArrayList<>();

    protected LoanApplication() {
    }

    public LoanApplication(String applicationNumber, UUID customerId, UUID productId,
                           String currency, BigDecimal requestedAmount, int requestedInstallments) {
        this.applicationNumber = applicationNumber;
        this.customerId = customerId;
        this.productId = productId;
        this.currency = currency;
        this.requestedAmount = requestedAmount;
        this.requestedInstallments = requestedInstallments;
    }

    /** Guards every state change; the message names both states so support can act on it. */
    private void requireTransition(LoanApplicationStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new ApiExceptions.IllegalStateTransitionException("Loan application", status, target);
        }
    }

    public void requireEditable() {
        if (!status.isEditable()) {
            throw new ApiExceptions.BusinessRuleException(
                    "Only a draft application can be edited; this one is " + status);
        }
    }

    public void setTerms(BigDecimal annualInterestRate, InterestMethod interestMethod,
                         RepaymentFrequency repaymentFrequency, GraceType graceType,
                         int gracePeriods) {
        this.annualInterestRate = annualInterestRate;
        this.interestMethod = interestMethod;
        this.repaymentFrequency = repaymentFrequency;
        this.graceType = graceType == null ? GraceType.NONE : graceType;
        this.gracePeriods = gracePeriods;
    }

    public void setRequested(BigDecimal amount, int installments, String purpose,
                             LocalDate expectedDisbursementDate, LocalDate firstRepaymentDate) {
        this.requestedAmount = amount;
        this.requestedInstallments = installments;
        this.purpose = purpose;
        this.expectedDisbursementDate = expectedDisbursementDate;
        this.firstRepaymentDate = firstRepaymentDate;
    }

    public void describe(String customerName, String productName, UUID branchId, UUID loanOfficerId,
                         int requiredApprovalLevels) {
        this.customerName = customerName;
        this.productName = productName;
        this.branchId = branchId;
        this.loanOfficerId = loanOfficerId;
        this.requiredApprovalLevels = Math.max(1, requiredApprovalLevels);
    }

    public void submit() {
        requireTransition(LoanApplicationStatus.SUBMITTED);
        this.status = LoanApplicationStatus.SUBMITTED;
        this.submittedAt = Instant.now();
    }

    public void beginReview(UUID reviewerId, String comment) {
        requireTransition(LoanApplicationStatus.UNDER_REVIEW);
        this.status = LoanApplicationStatus.UNDER_REVIEW;
        this.approvals.add(ApprovalRecord.review(currentApprovalLevel + 1, reviewerId, comment));
    }

    /**
     * Records one approval. The application only becomes APPROVED once every configured level
     * has signed off, which is how a product's approval hierarchy is enforced.
     *
     * @return true when this approval completed the workflow
     */
    public boolean approve(UUID approverId, String comment, BigDecimal approvedAmount,
                           Integer approvedInstallments) {
        if (status != LoanApplicationStatus.SUBMITTED && status != LoanApplicationStatus.UNDER_REVIEW) {
            throw new ApiExceptions.IllegalStateTransitionException("Loan application", status,
                    LoanApplicationStatus.APPROVED);
        }
        if (hasAlreadyDecided(approverId)) {
            // Two levels of approval from one person is not two levels of approval.
            throw new ApiExceptions.BusinessRuleException(
                    "You have already recorded a decision on this application");
        }
        this.currentApprovalLevel++;
        this.approvals.add(ApprovalRecord.approval(currentApprovalLevel, approverId, comment,
                approvedAmount, approvedInstallments));

        if (approvedAmount != null) {
            this.approvedAmount = approvedAmount;
        }
        if (approvedInstallments != null) {
            this.approvedInstallments = approvedInstallments;
        }

        if (currentApprovalLevel >= requiredApprovalLevels) {
            this.status = LoanApplicationStatus.APPROVED;
            this.decidedAt = Instant.now();
            // Falling back to the requested figures keeps a single-level approval simple.
            if (this.approvedAmount == null) {
                this.approvedAmount = requestedAmount;
            }
            if (this.approvedInstallments == null) {
                this.approvedInstallments = requestedInstallments;
            }
            return true;
        }
        this.status = LoanApplicationStatus.UNDER_REVIEW;
        return false;
    }

    public void reject(UUID approverId, String reason) {
        requireTransition(LoanApplicationStatus.REJECTED);
        this.status = LoanApplicationStatus.REJECTED;
        this.rejectionReason = reason;
        this.decidedAt = Instant.now();
        this.approvals.add(ApprovalRecord.rejection(currentApprovalLevel + 1, approverId, reason));
    }

    public void cancel(UUID actorId, String reason) {
        requireTransition(LoanApplicationStatus.CANCELLED);
        this.status = LoanApplicationStatus.CANCELLED;
        this.rejectionReason = reason;
        this.decidedAt = Instant.now();
        this.approvals.add(ApprovalRecord.cancellation(currentApprovalLevel + 1, actorId, reason));
    }

    public void markDisbursed(DisbursementMethod method, String reference, LocalDate date,
                              BigDecimal amount, BigDecimal netAmount) {
        requireTransition(LoanApplicationStatus.DISBURSED);
        if (amount == null || amount.compareTo(approvedAmount) != 0) {
            // Disbursing anything other than the approved amount would bypass the approval.
            throw new ApiExceptions.BusinessRuleException(
                    "The disbursed amount must equal the approved amount of " + approvedAmount);
        }
        this.status = LoanApplicationStatus.DISBURSED;
        this.disbursementMethod = method;
        this.disbursementReference = reference;
        this.disbursementDate = date;
        this.disbursedAmount = amount;
        this.netDisbursedAmount = netAmount;
        this.disbursedAt = Instant.now();
    }

    private boolean hasAlreadyDecided(UUID approverId) {
        return approvals.stream()
                .filter(record -> record.getDecision() == ApprovalDecision.APPROVED)
                .anyMatch(record -> approverId.equals(record.getDecidedBy()));
    }

    /** The figures the loan account will actually be opened with. */
    public BigDecimal effectiveAmount() {
        return approvedAmount != null ? approvedAmount : requestedAmount;
    }

    public int effectiveInstallments() {
        return approvedInstallments != null ? approvedInstallments : requestedInstallments;
    }

    public String getApplicationNumber() {
        return applicationNumber;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    public int getRequestedInstallments() {
        return requestedInstallments;
    }

    public BigDecimal getApprovedAmount() {
        return approvedAmount;
    }

    public Integer getApprovedInstallments() {
        return approvedInstallments;
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

    public GraceType getGraceType() {
        return graceType;
    }

    public int getGracePeriods() {
        return gracePeriods;
    }

    public String getPurpose() {
        return purpose;
    }

    public LocalDate getExpectedDisbursementDate() {
        return expectedDisbursementDate;
    }

    public LocalDate getFirstRepaymentDate() {
        return firstRepaymentDate;
    }

    public LoanApplicationStatus getStatus() {
        return status;
    }

    public int getCurrentApprovalLevel() {
        return currentApprovalLevel;
    }

    public int getRequiredApprovalLevels() {
        return requiredApprovalLevels;
    }

    public UUID getBranchId() {
        return branchId;
    }

    public UUID getLoanOfficerId() {
        return loanOfficerId;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getDisbursedAt() {
        return disbursedAt;
    }

    public LocalDate getDisbursementDate() {
        return disbursementDate;
    }

    public DisbursementMethod getDisbursementMethod() {
        return disbursementMethod;
    }

    public String getDisbursementReference() {
        return disbursementReference;
    }

    public BigDecimal getDisbursedAmount() {
        return disbursedAmount;
    }

    public BigDecimal getNetDisbursedAmount() {
        return netDisbursedAmount;
    }

    public List<ApprovalRecord> getApprovals() {
        return List.copyOf(approvals);
    }
}

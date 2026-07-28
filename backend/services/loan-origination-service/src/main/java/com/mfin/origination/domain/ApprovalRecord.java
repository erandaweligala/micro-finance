package com.mfin.origination.domain;

import com.mfin.common.persistence.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One entry in an application's approval history.
 *
 * <p>Append-only: entries are never updated or deleted, because the sequence of who decided
 * what, when and why is exactly what an auditor or a regulator comes to read.</p>
 */
@Entity
@Table(name = "approval_record",
        indexes = @Index(name = "ix_approval_application", columnList = "tenant_id, application_id"))
@Filter(name = TenantAwareEntity.FILTER, condition = TenantAwareEntity.CONDITION)
public class ApprovalRecord extends TenantAwareEntity {

    @Column(name = "application_id", insertable = false, updatable = false,
            columnDefinition = "CHAR(36)")
    private UUID applicationId;

    @Column(name = "approval_level", nullable = false)
    private int approvalLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 24)
    private ApprovalDecision decision;

    @Column(name = "decided_by", columnDefinition = "CHAR(36)")
    private UUID decidedBy;

    @Column(name = "comment", length = 1024)
    private String comment;

    /** Populated when an approver sanctioned less than was requested. */
    @Column(name = "approved_amount", precision = 19, scale = 4)
    private BigDecimal approvedAmount;

    @Column(name = "approved_installments")
    private Integer approvedInstallments;

    protected ApprovalRecord() {
    }

    private ApprovalRecord(int approvalLevel, ApprovalDecision decision, UUID decidedBy,
                           String comment, BigDecimal approvedAmount, Integer approvedInstallments) {
        this.approvalLevel = approvalLevel;
        this.decision = decision;
        this.decidedBy = decidedBy;
        this.comment = comment;
        this.approvedAmount = approvedAmount;
        this.approvedInstallments = approvedInstallments;
    }

    public static ApprovalRecord review(int level, UUID reviewerId, String comment) {
        return new ApprovalRecord(level, ApprovalDecision.REVIEWED, reviewerId, comment, null, null);
    }

    public static ApprovalRecord approval(int level, UUID approverId, String comment,
                                          BigDecimal approvedAmount, Integer approvedInstallments) {
        return new ApprovalRecord(level, ApprovalDecision.APPROVED, approverId, comment,
                approvedAmount, approvedInstallments);
    }

    public static ApprovalRecord rejection(int level, UUID approverId, String reason) {
        return new ApprovalRecord(level, ApprovalDecision.REJECTED, approverId, reason, null, null);
    }

    public static ApprovalRecord cancellation(int level, UUID actorId, String reason) {
        return new ApprovalRecord(level, ApprovalDecision.CANCELLED, actorId, reason, null, null);
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public int getApprovalLevel() {
        return approvalLevel;
    }

    public ApprovalDecision getDecision() {
        return decision;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public String getComment() {
        return comment;
    }

    public BigDecimal getApprovedAmount() {
        return approvedAmount;
    }

    public Integer getApprovedInstallments() {
        return approvedInstallments;
    }
}

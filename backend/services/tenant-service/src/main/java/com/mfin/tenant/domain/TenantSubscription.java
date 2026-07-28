package com.mfin.tenant.domain;

import com.mfin.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/** An institution's current plan and billing period. */
@Entity
@Table(name = "tenant_subscription",
        indexes = @Index(name = "ix_subscription_tenant", columnList = "tenant_id"))
public class TenantSubscription extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID tenantId;

    @Column(name = "plan_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID planId;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    /** Null means open-ended. */
    @Column(name = "ends_on")
    private LocalDate endsOn;

    @Column(name = "trial_ends_on")
    private LocalDate trialEndsOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private SubscriptionStatus status = SubscriptionStatus.TRIAL;

    public enum SubscriptionStatus {
        TRIAL, ACTIVE, PAST_DUE, CANCELLED
    }

    protected TenantSubscription() {
    }

    public TenantSubscription(UUID tenantId, UUID planId, LocalDate startsOn) {
        this.tenantId = tenantId;
        this.planId = planId;
        this.startsOn = startsOn;
    }

    /** True while the institution is entitled to use the platform. */
    public boolean isCurrent(LocalDate on) {
        boolean started = !startsOn.isAfter(on);
        boolean notEnded = endsOn == null || !endsOn.isBefore(on);
        return started && notEnded
                && (status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.TRIAL);
    }

    public void changePlan(UUID newPlanId, LocalDate effectiveFrom) {
        this.planId = newPlanId;
        this.startsOn = effectiveFrom;
    }

    public void activate() {
        this.status = SubscriptionStatus.ACTIVE;
    }

    public void markPastDue() {
        this.status = SubscriptionStatus.PAST_DUE;
    }

    public void cancel(LocalDate endsOn) {
        this.status = SubscriptionStatus.CANCELLED;
        this.endsOn = endsOn;
    }

    public void startTrial(LocalDate trialEndsOn) {
        this.status = SubscriptionStatus.TRIAL;
        this.trialEndsOn = trialEndsOn;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getPlanId() {
        return planId;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    public LocalDate getTrialEndsOn() {
        return trialEndsOn;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }
}

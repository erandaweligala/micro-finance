package com.mfin.tenant.domain;

import com.mfin.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A SaaS plan: what an institution may do and how much of it.
 *
 * <p>Limits are enforced at the point of use (creating a user, registering a customer, opening
 * a loan) rather than by a nightly sweep, so an institution can never quietly exceed what it
 * pays for.</p>
 */
@Entity
@Table(name = "subscription_plan",
        uniqueConstraints = @UniqueConstraint(name = "ux_plan_code", columnNames = "code"))
public class SubscriptionPlan extends BaseEntity {

    @Column(name = "code", nullable = false, length = 32, updatable = false)
    private String code;

    @Column(name = "name", nullable = false, length = 96)
    private String name;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "monthly_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal monthlyPrice = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    /** Zero means unlimited, which is how the enterprise plan is expressed. */
    @Column(name = "max_users", nullable = false)
    private int maxUsers;

    @Column(name = "max_customers", nullable = false)
    private int maxCustomers;

    @Column(name = "max_active_loans", nullable = false)
    private int maxActiveLoans;

    @Column(name = "max_branches", nullable = false)
    private int maxBranches;

    /** Named capabilities, e.g. {@code SMS_NOTIFICATIONS}, {@code API_ACCESS}, {@code CUSTOM_BRANDING}. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "subscription_plan_feature",
            joinColumns = @JoinColumn(name = "plan_id"))
    @Column(name = "feature", nullable = false, length = 48)
    private Set<String> features = new LinkedHashSet<>();

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected SubscriptionPlan() {
    }

    public SubscriptionPlan(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public void configure(String name, String description, BigDecimal monthlyPrice, String currency,
                          int maxUsers, int maxCustomers, int maxActiveLoans, int maxBranches,
                          Set<String> features) {
        this.name = name;
        this.description = description;
        this.monthlyPrice = monthlyPrice;
        this.currency = currency;
        this.maxUsers = maxUsers;
        this.maxCustomers = maxCustomers;
        this.maxActiveLoans = maxActiveLoans;
        this.maxBranches = maxBranches;
        this.features = new LinkedHashSet<>(features);
    }

    public boolean includes(String feature) {
        return features.contains(feature);
    }

    /** A limit of zero means unlimited. */
    public boolean withinLimit(int limit, long current) {
        return limit <= 0 || current < limit;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getMonthlyPrice() {
        return monthlyPrice;
    }

    public String getCurrency() {
        return currency;
    }

    public int getMaxUsers() {
        return maxUsers;
    }

    public int getMaxCustomers() {
        return maxCustomers;
    }

    public int getMaxActiveLoans() {
        return maxActiveLoans;
    }

    public int getMaxBranches() {
        return maxBranches;
    }

    public Set<String> getFeatures() {
        return Set.copyOf(features);
    }

    public boolean isActive() {
        return active;
    }

    public void deactivate() {
        this.active = false;
    }
}

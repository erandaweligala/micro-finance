package com.mfin.tenant.domain;

import com.mfin.common.error.ApiExceptions;
import com.mfin.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A microfinance institution on the platform.
 *
 * <p>This is the one aggregate that is deliberately <em>not</em> tenant-scoped: it defines the
 * tenants, so scoping it to a tenant would be circular. Access is restricted to platform
 * administrators, and a tenant administrator may only read their own row.</p>
 */
@Entity
@Table(name = "tenant", uniqueConstraints = {
        @UniqueConstraint(name = "ux_tenant_slug", columnNames = "slug"),
        @UniqueConstraint(name = "ux_tenant_subdomain", columnNames = "subdomain")
})
public class Tenant extends BaseEntity {

    /** Stable identifier used at sign-in and in URLs, e.g. {@code acme-microfinance}. */
    @Column(name = "slug", nullable = false, length = 64, updatable = false)
    private String slug;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "legal_name", length = 200)
    private String legalName;

    @Column(name = "registration_number", length = 64)
    private String registrationNumber;

    /** Host the institution's staff sign in from; an alternative to entering the slug. */
    @Column(name = "subdomain", length = 96)
    private String subdomain;

    @Column(name = "contact_email", nullable = false, length = 255)
    private String contactEmail;

    @Column(name = "contact_phone", length = 32)
    private String contactPhone;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "default_currency", nullable = false, length = 3)
    private String defaultCurrency = "KES";

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = "UTC";

    // ---- Branding, applied by the mobile app ----

    @Column(name = "logo_url", length = 512)
    private String logoUrl;

    @Column(name = "primary_color", length = 9)
    private String primaryColor;

    @Column(name = "secondary_color", length = 9)
    private String secondaryColor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private TenantStatus status = TenantStatus.PENDING_ACTIVATION;

    @Column(name = "onboarded_on")
    private LocalDate onboardedOn;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "suspension_reason", length = 512)
    private String suspensionReason;

    protected Tenant() {
    }

    public Tenant(String slug, String name, String contactEmail) {
        this.slug = slug;
        this.name = name;
        this.contactEmail = contactEmail;
    }

    public void updateProfile(String name, String legalName, String registrationNumber,
                              String contactEmail, String contactPhone, String countryCode,
                              String defaultCurrency, String timezone, String subdomain) {
        this.name = name;
        this.legalName = legalName;
        this.registrationNumber = registrationNumber;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.countryCode = countryCode;
        this.defaultCurrency = defaultCurrency;
        this.timezone = timezone;
        this.subdomain = subdomain;
    }

    public void updateBranding(String logoUrl, String primaryColor, String secondaryColor) {
        this.logoUrl = logoUrl;
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
    }

    public void activate() {
        this.status = TenantStatus.ACTIVE;
        this.suspendedAt = null;
        this.suspensionReason = null;
        if (this.onboardedOn == null) {
            this.onboardedOn = LocalDate.now();
        }
    }

    /** Suspension stops sign-in for the whole institution; existing loans are untouched. */
    public void suspend(String reason) {
        if (status == TenantStatus.SUSPENDED) {
            throw new ApiExceptions.BusinessRuleException("This institution is already suspended");
        }
        this.status = TenantStatus.SUSPENDED;
        this.suspendedAt = Instant.now();
        this.suspensionReason = reason;
    }

    public boolean canSignIn() {
        return status == TenantStatus.ACTIVE;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public String getSubdomain() {
        return subdomain;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getDefaultCurrency() {
        return defaultCurrency;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public String getPrimaryColor() {
        return primaryColor;
    }

    public String getSecondaryColor() {
        return secondaryColor;
    }

    public TenantStatus getStatus() {
        return status;
    }

    public LocalDate getOnboardedOn() {
        return onboardedOn;
    }

    public String getSuspensionReason() {
        return suspensionReason;
    }
}

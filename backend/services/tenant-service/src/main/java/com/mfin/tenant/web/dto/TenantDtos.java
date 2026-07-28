package com.mfin.tenant.web.dto;

import com.mfin.tenant.domain.Branch;
import com.mfin.tenant.domain.SubscriptionPlan;
import com.mfin.tenant.domain.Tenant;
import com.mfin.tenant.domain.TenantStatus;
import com.mfin.tenant.domain.TenantSubscription;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Tenant onboarding and administration payloads. */
public final class TenantDtos {

    private TenantDtos() {
    }

    @Schema(description = "Onboard a new microfinance institution and its first administrator")
    public record OnboardTenantRequest(
            @NotBlank @Size(max = 64)
            @Pattern(regexp = "^[a-z0-9-]+$",
                    message = "Slug must be lower-case letters, digits and hyphens")
            @Schema(example = "acme-microfinance")
            String slug,

            @NotBlank @Size(max = 160) String name,
            @Size(max = 200) String legalName,
            @Size(max = 64) String registrationNumber,
            @Size(max = 96) String subdomain,

            @NotBlank @Email @Size(max = 255) String contactEmail,
            @Size(max = 32) String contactPhone,
            @Pattern(regexp = "^[A-Z]{2}$") String countryCode,
            @Pattern(regexp = "^[A-Z]{3}$") String defaultCurrency,
            @Size(max = 64) String timezone,

            @NotBlank @Size(max = 32)
            @Schema(description = "Subscription plan code", example = "GROWTH")
            String planCode,

            @Schema(description = "Days of free trial; 0 starts billing immediately")
            @PositiveOrZero int trialDays,

            @NotNull @Valid AdminAccountRequest administrator
    ) {
    }

    @Schema(description = "The institution's first administrator account")
    public record AdminAccountRequest(
            @NotBlank @Size(max = 128) String username,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 160) String fullName,
            @NotBlank @Size(min = 12, max = 128)
            @Schema(description = "Temporary password; the administrator must change it at first sign-in")
            String temporaryPassword
    ) {
    }

    public record UpdateTenantRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 200) String legalName,
            @Size(max = 64) String registrationNumber,
            @NotBlank @Email String contactEmail,
            @Size(max = 32) String contactPhone,
            @Pattern(regexp = "^[A-Z]{2}$") String countryCode,
            @Pattern(regexp = "^[A-Z]{3}$") String defaultCurrency,
            @Size(max = 64) String timezone,
            @Size(max = 96) String subdomain
    ) {
    }

    public record BrandingRequest(
            @Size(max = 512) String logoUrl,
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Colour must be a hex value like #1B5E20")
            String primaryColor,
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String secondaryColor
    ) {
    }

    public record SuspendTenantRequest(
            @NotBlank @Size(max = 512) String reason
    ) {
    }

    public record TenantResponse(
            UUID id,
            String slug,
            String name,
            String legalName,
            String registrationNumber,
            String subdomain,
            String contactEmail,
            String contactPhone,
            String countryCode,
            String defaultCurrency,
            String timezone,
            String logoUrl,
            String primaryColor,
            String secondaryColor,
            TenantStatus status,
            LocalDate onboardedOn,
            String suspensionReason
    ) {
        public static TenantResponse from(Tenant tenant) {
            return new TenantResponse(tenant.getId(), tenant.getSlug(), tenant.getName(),
                    tenant.getLegalName(), tenant.getRegistrationNumber(), tenant.getSubdomain(),
                    tenant.getContactEmail(), tenant.getContactPhone(), tenant.getCountryCode(),
                    tenant.getDefaultCurrency(), tenant.getTimezone(), tenant.getLogoUrl(),
                    tenant.getPrimaryColor(), tenant.getSecondaryColor(), tenant.getStatus(),
                    tenant.getOnboardedOn(), tenant.getSuspensionReason());
        }
    }

    @Schema(description = "Public branding for the sign-in screen; safe to fetch without a token")
    public record TenantBrandingResponse(
            String slug,
            String name,
            String logoUrl,
            String primaryColor,
            String secondaryColor,
            String defaultCurrency,
            boolean loginEnabled
    ) {
        public static TenantBrandingResponse from(Tenant tenant) {
            return new TenantBrandingResponse(tenant.getSlug(), tenant.getName(),
                    tenant.getLogoUrl(), tenant.getPrimaryColor(), tenant.getSecondaryColor(),
                    tenant.getDefaultCurrency(), tenant.canSignIn());
        }
    }

    public record PlanRequest(
            @NotBlank @Size(max = 32) String code,
            @NotBlank @Size(max = 96) String name,
            @Size(max = 512) String description,
            @NotNull @PositiveOrZero BigDecimal monthlyPrice,
            @Pattern(regexp = "^[A-Z]{3}$") String currency,
            @PositiveOrZero int maxUsers,
            @PositiveOrZero int maxCustomers,
            @PositiveOrZero int maxActiveLoans,
            @PositiveOrZero int maxBranches,
            Set<String> features
    ) {
    }

    public record PlanResponse(
            UUID id,
            String code,
            String name,
            String description,
            BigDecimal monthlyPrice,
            String currency,
            @Schema(description = "0 means unlimited") int maxUsers,
            int maxCustomers,
            int maxActiveLoans,
            int maxBranches,
            Set<String> features,
            boolean active
    ) {
        public static PlanResponse from(SubscriptionPlan plan) {
            return new PlanResponse(plan.getId(), plan.getCode(), plan.getName(),
                    plan.getDescription(), plan.getMonthlyPrice(), plan.getCurrency(),
                    plan.getMaxUsers(), plan.getMaxCustomers(), plan.getMaxActiveLoans(),
                    plan.getMaxBranches(), plan.getFeatures(), plan.isActive());
        }
    }

    public record SubscriptionResponse(
            UUID id,
            UUID tenantId,
            UUID planId,
            String planCode,
            LocalDate startsOn,
            LocalDate endsOn,
            LocalDate trialEndsOn,
            TenantSubscription.SubscriptionStatus status,
            Set<String> features,
            int maxUsers,
            int maxCustomers,
            int maxActiveLoans,
            int maxBranches
    ) {
    }

    public record BranchRequest(
            @NotBlank @Size(max = 24) String code,
            @NotBlank @Size(max = 128) String name,
            @Size(max = 200) String addressLine,
            @Size(max = 96) String city,
            @Size(max = 32) String phone
    ) {
    }

    public record BranchResponse(UUID id, String code, String name, String addressLine,
                                 String city, String phone, boolean active) {
        public static BranchResponse from(Branch branch) {
            return new BranchResponse(branch.getId(), branch.getCode(), branch.getName(),
                    branch.getAddressLine(), branch.getCity(), branch.getPhone(), branch.isActive());
        }
    }

    public record SettingRequest(
            @NotBlank @Size(max = 96) String key,
            @NotBlank @Size(max = 1024) String value,
            @Size(max = 256) String description
    ) {
    }

    public record SettingResponse(String key, String value, String description) {
    }

    public record OnboardingResponse(
            TenantResponse tenant,
            SubscriptionResponse subscription,
            @Schema(description = "The administrator account created for this institution")
            UUID administratorUserId,
            List<String> nextSteps
    ) {
    }
}

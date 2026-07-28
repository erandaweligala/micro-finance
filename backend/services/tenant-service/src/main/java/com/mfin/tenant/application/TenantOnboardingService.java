package com.mfin.tenant.application;

import com.mfin.common.error.ApiExceptions;
import com.mfin.common.events.TenantEvents;
import com.mfin.common.outbox.DomainEventPublisher;
import com.mfin.tenant.client.IdentityClient;
import com.mfin.tenant.domain.SubscriptionPlan;
import com.mfin.tenant.domain.Tenant;
import com.mfin.tenant.domain.TenantSetting;
import com.mfin.tenant.domain.TenantSubscription;
import com.mfin.tenant.repository.TenantRepositories.SubscriptionPlanRepository;
import com.mfin.tenant.repository.TenantRepositories.TenantRepository;
import com.mfin.tenant.repository.TenantRepositories.TenantSettingRepository;
import com.mfin.tenant.repository.TenantRepositories.TenantSubscriptionRepository;
import com.mfin.tenant.web.dto.TenantDtos.OnboardTenantRequest;
import com.mfin.tenant.web.dto.TenantDtos.OnboardingResponse;
import com.mfin.tenant.web.dto.TenantDtos.SubscriptionResponse;
import com.mfin.tenant.web.dto.TenantDtos.TenantResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Onboards a microfinance institution onto the platform.
 *
 * <p>Onboarding spans two services - the tenant record lives here, the administrator account
 * lives in identity - so it is a small saga rather than one transaction:</p>
 * <ol>
 *   <li>The tenant, subscription and default settings are committed here.</li>
 *   <li>The administrator is created in the identity service.</li>
 *   <li>If step 2 fails, the tenant is left {@code PENDING_ACTIVATION} and the caller is told
 *       plainly. Nobody can sign in to a pending tenant, so a half-finished onboarding is
 *       inert rather than dangerous, and it can be retried without creating a duplicate.</li>
 * </ol>
 * The alternative - a distributed transaction across two databases - buys consistency the
 * business does not need at a cost in availability it cannot afford.
 */
@Service
public class TenantOnboardingService {

    private static final Logger log = LoggerFactory.getLogger(TenantOnboardingService.class);

    /** Settings every new institution starts with; each can be changed afterwards. */
    private static final Map<String, String> DEFAULT_SETTINGS = Map.of(
            "loan.allocation-order", "PENALTY_FEE_INTEREST_PRINCIPAL",
            "loan.working-days", "MON,TUE,WED,THU,FRI",
            "notifications.repayment-reminder-days", "3",
            "statement.footer", "Thank you for banking with us.");

    private final TenantRepository tenantRepository;
    private final SubscriptionPlanRepository planRepository;
    private final TenantSubscriptionRepository subscriptionRepository;
    private final TenantSettingRepository settingRepository;
    private final IdentityClient identityClient;
    private final DomainEventPublisher eventPublisher;

    public TenantOnboardingService(TenantRepository tenantRepository,
                                   SubscriptionPlanRepository planRepository,
                                   TenantSubscriptionRepository subscriptionRepository,
                                   TenantSettingRepository settingRepository,
                                   IdentityClient identityClient,
                                   DomainEventPublisher eventPublisher) {
        this.tenantRepository = tenantRepository;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.settingRepository = settingRepository;
        this.identityClient = identityClient;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public OnboardingResponse onboard(OnboardTenantRequest request, String bearerToken) {
        if (tenantRepository.existsBySlugIgnoreCase(request.slug())) {
            throw new ApiExceptions.ConflictException(
                    "An institution with the identifier '" + request.slug() + "' already exists");
        }
        SubscriptionPlan plan = planRepository.findByCodeIgnoreCase(request.planCode())
                .filter(SubscriptionPlan::isActive)
                .orElseThrow(() -> new ApiExceptions.BusinessRuleException(
                        "Subscription plan '" + request.planCode() + "' is not available"));

        Tenant tenant = new Tenant(request.slug().toLowerCase(), request.name(),
                request.contactEmail());
        tenant.updateProfile(request.name(), request.legalName(), request.registrationNumber(),
                request.contactEmail(), request.contactPhone(), request.countryCode(),
                request.defaultCurrency() == null ? "KES" : request.defaultCurrency(),
                request.timezone() == null ? "UTC" : request.timezone(), request.subdomain());
        Tenant savedTenant = tenantRepository.save(tenant);

        LocalDate today = LocalDate.now();
        TenantSubscription subscription =
                new TenantSubscription(savedTenant.getId(), plan.getId(), today);
        if (request.trialDays() > 0) {
            subscription.startTrial(today.plusDays(request.trialDays()));
        } else {
            subscription.activate();
        }
        TenantSubscription savedSubscription = subscriptionRepository.save(subscription);

        DEFAULT_SETTINGS.forEach((key, value) -> {
            TenantSetting setting = new TenantSetting(key, value, "Platform default");
            setting.setTenantId(savedTenant.getId());
            settingRepository.save(setting);
        });

        // Cross-service step. A failure here leaves the tenant inert (PENDING_ACTIVATION),
        // which is a safe state to retry from.
        UUID administratorId;
        try {
            administratorId = identityClient.provisionTenantAdmin(savedTenant.getId(),
                    savedTenant.getSlug(), savedTenant.getName(), request.administrator(),
                    bearerToken);
        } catch (RuntimeException ex) {
            log.error("Tenant {} was created but its administrator could not be provisioned",
                    savedTenant.getSlug(), ex);
            throw new ApiExceptions.ConflictException(
                    "The institution was created but its administrator account could not be set up. "
                            + "Retry onboarding for this institution to complete it.");
        }

        savedTenant.activate();
        tenantRepository.save(savedTenant);

        eventPublisher.publish(new TenantEvents.TenantOnboarded(UUID.randomUUID(),
                savedTenant.getId(), savedTenant.getSlug(), savedTenant.getName(),
                plan.getCode(), Instant.now()));

        log.info("Onboarded institution {} on plan {}", savedTenant.getSlug(), plan.getCode());
        return new OnboardingResponse(
                TenantResponse.from(savedTenant),
                toSubscriptionResponse(savedSubscription, plan),
                administratorId,
                List.of("Sign in as the administrator and change the temporary password",
                        "Create branches and staff accounts",
                        "Configure at least one loan product and activate it",
                        "Register customers and complete their KYC"));
    }

    private SubscriptionResponse toSubscriptionResponse(TenantSubscription subscription,
                                                        SubscriptionPlan plan) {
        return new SubscriptionResponse(subscription.getId(), subscription.getTenantId(),
                plan.getId(), plan.getCode(), subscription.getStartsOn(), subscription.getEndsOn(),
                subscription.getTrialEndsOn(), subscription.getStatus(), plan.getFeatures(),
                plan.getMaxUsers(), plan.getMaxCustomers(), plan.getMaxActiveLoans(),
                plan.getMaxBranches());
    }
}

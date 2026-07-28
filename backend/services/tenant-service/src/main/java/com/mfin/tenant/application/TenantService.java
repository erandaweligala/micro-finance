package com.mfin.tenant.application;

import com.mfin.common.error.ApiExceptions;
import com.mfin.common.events.TenantEvents;
import com.mfin.common.outbox.DomainEventPublisher;
import com.mfin.common.tenant.TenantContext;
import com.mfin.common.web.PageResponse;
import com.mfin.tenant.domain.Branch;
import com.mfin.tenant.domain.SubscriptionPlan;
import com.mfin.tenant.domain.Tenant;
import com.mfin.tenant.domain.TenantSetting;
import com.mfin.tenant.domain.TenantStatus;
import com.mfin.tenant.domain.TenantSubscription;
import com.mfin.tenant.repository.TenantRepositories.BranchRepository;
import com.mfin.tenant.repository.TenantRepositories.SubscriptionPlanRepository;
import com.mfin.tenant.repository.TenantRepositories.TenantRepository;
import com.mfin.tenant.repository.TenantRepositories.TenantSettingRepository;
import com.mfin.tenant.repository.TenantRepositories.TenantSubscriptionRepository;
import com.mfin.tenant.web.dto.TenantDtos.BranchRequest;
import com.mfin.tenant.web.dto.TenantDtos.BranchResponse;
import com.mfin.tenant.web.dto.TenantDtos.BrandingRequest;
import com.mfin.tenant.web.dto.TenantDtos.SettingRequest;
import com.mfin.tenant.web.dto.TenantDtos.SettingResponse;
import com.mfin.tenant.web.dto.TenantDtos.SubscriptionResponse;
import com.mfin.tenant.web.dto.TenantDtos.TenantBrandingResponse;
import com.mfin.tenant.web.dto.TenantDtos.TenantResponse;
import com.mfin.tenant.web.dto.TenantDtos.UpdateTenantRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Tenant administration, branches, settings and subscription enquiries. */
@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final SubscriptionPlanRepository planRepository;
    private final TenantSubscriptionRepository subscriptionRepository;
    private final BranchRepository branchRepository;
    private final TenantSettingRepository settingRepository;
    private final DomainEventPublisher eventPublisher;

    public TenantService(TenantRepository tenantRepository,
                         SubscriptionPlanRepository planRepository,
                         TenantSubscriptionRepository subscriptionRepository,
                         BranchRepository branchRepository,
                         TenantSettingRepository settingRepository,
                         DomainEventPublisher eventPublisher) {
        this.tenantRepository = tenantRepository;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.branchRepository = branchRepository;
        this.settingRepository = settingRepository;
        this.eventPublisher = eventPublisher;
    }

    // ------------------------------------------------ platform administration

    @Transactional(readOnly = true)
    public PageResponse<TenantResponse> search(TenantStatus status, String query, Pageable pageable) {
        String normalised = query == null || query.isBlank() ? null : query.trim();
        return PageResponse.from(tenantRepository.search(status, normalised, pageable),
                TenantResponse::from);
    }

    @Transactional(readOnly = true)
    public TenantResponse get(UUID tenantId) {
        return TenantResponse.from(require(tenantId));
    }

    @Transactional
    public TenantResponse suspend(UUID tenantId, String reason) {
        Tenant tenant = require(tenantId);
        tenant.suspend(reason);
        Tenant saved = tenantRepository.save(tenant);
        publishStatusChange(saved, reason);
        return TenantResponse.from(saved);
    }

    @Transactional
    public TenantResponse activate(UUID tenantId) {
        Tenant tenant = require(tenantId);
        tenant.activate();
        Tenant saved = tenantRepository.save(tenant);
        publishStatusChange(saved, null);
        return TenantResponse.from(saved);
    }

    // ------------------------------------------------ the caller's own institution

    @Transactional(readOnly = true)
    public TenantResponse currentTenant() {
        return TenantResponse.from(require(TenantContext.requireTenantId()));
    }

    /**
     * Branding for the sign-in screen, looked up by slug.
     *
     * <p>Unauthenticated by necessity - the app needs the logo and colours before anyone has
     * signed in - so it returns only what is already public on the institution's shopfront, and
     * never reveals whether a slug exists beyond a 404.</p>
     */
    @Transactional(readOnly = true)
    public TenantBrandingResponse brandingBySlug(String slug) {
        return tenantRepository.findBySlugIgnoreCase(slug)
                .map(TenantBrandingResponse::from)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException(
                        "No institution matches that identifier"));
    }

    @Transactional
    public TenantResponse updateCurrent(UpdateTenantRequest request) {
        Tenant tenant = require(TenantContext.requireTenantId());
        tenant.updateProfile(request.name(), request.legalName(), request.registrationNumber(),
                request.contactEmail(), request.contactPhone(), request.countryCode(),
                request.defaultCurrency(), request.timezone(), request.subdomain());
        return TenantResponse.from(tenantRepository.save(tenant));
    }

    @Transactional
    public TenantResponse updateBranding(BrandingRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        requireFeature(tenantId, "CUSTOM_BRANDING");
        Tenant tenant = require(tenantId);
        tenant.updateBranding(request.logoUrl(), request.primaryColor(), request.secondaryColor());
        return TenantResponse.from(tenantRepository.save(tenant));
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse currentSubscription() {
        UUID tenantId = TenantContext.requireTenantId();
        TenantSubscription subscription = requireSubscription(tenantId);
        SubscriptionPlan plan = planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException(
                        "Subscription plan", subscription.getPlanId()));
        return new SubscriptionResponse(subscription.getId(), tenantId, plan.getId(),
                plan.getCode(), subscription.getStartsOn(), subscription.getEndsOn(),
                subscription.getTrialEndsOn(), subscription.getStatus(), plan.getFeatures(),
                plan.getMaxUsers(), plan.getMaxCustomers(), plan.getMaxActiveLoans(),
                plan.getMaxBranches());
    }

    // ------------------------------------------------ branches

    @Transactional
    public BranchResponse createBranch(BranchRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        if (branchRepository.existsByTenantIdAndCodeIgnoreCase(tenantId, request.code())) {
            throw new ApiExceptions.ConflictException(
                    "A branch with code '" + request.code() + "' already exists");
        }
        // Plan limits are enforced where the resource is created, not by a nightly sweep.
        SubscriptionPlan plan = currentPlan(tenantId);
        long existing = branchRepository.countByTenantIdAndActiveTrue(tenantId);
        if (!plan.withinLimit(plan.getMaxBranches(), existing)) {
            throw new ApiExceptions.SubscriptionLimitException(
                    com.mfin.common.error.ErrorCodes.TENANT_LIMIT_EXCEEDED,
                    "Your plan allows " + plan.getMaxBranches() + " branches. Upgrade to add more.");
        }
        Branch branch = new Branch(request.code(), request.name());
        branch.update(request.name(), request.addressLine(), request.city(), request.phone());
        return BranchResponse.from(branchRepository.save(branch));
    }

    @Transactional(readOnly = true)
    public List<BranchResponse> listBranches() {
        return branchRepository.findByTenantIdOrderByNameAsc(TenantContext.requireTenantId())
                .stream().map(BranchResponse::from).toList();
    }

    @Transactional
    public BranchResponse updateBranch(UUID branchId, BranchRequest request) {
        Branch branch = branchRepository
                .findByIdAndTenantId(branchId, TenantContext.requireTenantId())
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("Branch", branchId));
        branch.update(request.name(), request.addressLine(), request.city(), request.phone());
        return BranchResponse.from(branchRepository.save(branch));
    }

    @Transactional
    public void deactivateBranch(UUID branchId) {
        Branch branch = branchRepository
                .findByIdAndTenantId(branchId, TenantContext.requireTenantId())
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("Branch", branchId));
        branch.deactivate();
        branchRepository.save(branch);
    }

    // ------------------------------------------------ settings

    @Transactional(readOnly = true)
    public List<SettingResponse> listSettings() {
        return settingRepository.findByTenantId(TenantContext.requireTenantId()).stream()
                .map(setting -> new SettingResponse(setting.getKey(), setting.getValue(),
                        setting.getDescription()))
                .toList();
    }

    @Transactional
    public SettingResponse upsertSetting(SettingRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        TenantSetting setting = settingRepository.findByTenantIdAndKey(tenantId, request.key())
                .orElseGet(() -> new TenantSetting(request.key(), request.value(),
                        request.description()));
        setting.setValue(request.value());
        TenantSetting saved = settingRepository.save(setting);
        return new SettingResponse(saved.getKey(), saved.getValue(), saved.getDescription());
    }

    // ------------------------------------------------ internals

    /** Throws unless the institution's plan includes the named capability. */
    private void requireFeature(UUID tenantId, String feature) {
        if (!currentPlan(tenantId).includes(feature)) {
            throw new ApiExceptions.SubscriptionLimitException(
                    com.mfin.common.error.ErrorCodes.FEATURE_NOT_ENABLED,
                    "Your plan does not include " + feature.toLowerCase().replace('_', ' '));
        }
    }

    private SubscriptionPlan currentPlan(UUID tenantId) {
        TenantSubscription subscription = requireSubscription(tenantId);
        if (!subscription.isCurrent(LocalDate.now())) {
            throw new ApiExceptions.SubscriptionLimitException(
                    com.mfin.common.error.ErrorCodes.TENANT_LIMIT_EXCEEDED,
                    "This institution's subscription is not active");
        }
        return planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException(
                        "Subscription plan", subscription.getPlanId()));
    }

    private TenantSubscription requireSubscription(UUID tenantId) {
        return subscriptionRepository.findFirstByTenantIdOrderByStartsOnDesc(tenantId)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException(
                        "This institution has no subscription"));
    }

    private Tenant require(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("Institution", tenantId));
    }

    private void publishStatusChange(Tenant tenant, String reason) {
        eventPublisher.publish(new TenantEvents.TenantStatusChanged(UUID.randomUUID(),
                tenant.getId(), tenant.getSlug(), tenant.getName(), tenant.getStatus().name(),
                tenant.canSignIn(), reason, Instant.now()));
    }
}

package com.mfin.tenant.web;

import com.mfin.common.tenant.Roles;
import com.mfin.common.web.PageResponse;
import com.mfin.tenant.application.TenantOnboardingService;
import com.mfin.tenant.application.TenantService;
import com.mfin.tenant.domain.TenantStatus;
import com.mfin.tenant.web.dto.TenantDtos.BranchRequest;
import com.mfin.tenant.web.dto.TenantDtos.BranchResponse;
import com.mfin.tenant.web.dto.TenantDtos.BrandingRequest;
import com.mfin.tenant.web.dto.TenantDtos.OnboardTenantRequest;
import com.mfin.tenant.web.dto.TenantDtos.OnboardingResponse;
import com.mfin.tenant.web.dto.TenantDtos.SettingRequest;
import com.mfin.tenant.web.dto.TenantDtos.SettingResponse;
import com.mfin.tenant.web.dto.TenantDtos.SubscriptionResponse;
import com.mfin.tenant.web.dto.TenantDtos.SuspendTenantRequest;
import com.mfin.tenant.web.dto.TenantDtos.TenantBrandingResponse;
import com.mfin.tenant.web.dto.TenantDtos.TenantResponse;
import com.mfin.tenant.web.dto.TenantDtos.UpdateTenantRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants")
@Tag(name = "Tenants", description = "Institution onboarding, subscriptions, branches and settings")
public class TenantController {

    private final TenantService tenantService;
    private final TenantOnboardingService onboardingService;

    public TenantController(TenantService tenantService,
                            TenantOnboardingService onboardingService) {
        this.tenantService = tenantService;
        this.onboardingService = onboardingService;
    }

    // ---------------------------------------------------------- platform operators

    @PostMapping
    @PreAuthorize(Roles.Has.PLATFORM_ADMIN)
    @Operation(summary = "Onboard a new institution",
            description = "Creates the institution, its subscription, its default settings and its "
                    + "first administrator account.")
    public ResponseEntity<OnboardingResponse> onboard(
            @Valid @RequestBody OnboardTenantRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken) {
        OnboardingResponse response = onboardingService.onboard(request, bearerToken);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize(Roles.Has.PLATFORM_ADMIN)
    @Operation(summary = "List all institutions on the platform")
    public PageResponse<TenantResponse> search(
            @RequestParam(required = false) TenantStatus status,
            @RequestParam(required = false) String query,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return tenantService.search(status, query, pageable);
    }

    @GetMapping("/{tenantId}")
    @PreAuthorize(Roles.Has.PLATFORM_ADMIN)
    @Operation(summary = "Fetch one institution")
    public TenantResponse get(@PathVariable UUID tenantId) {
        return tenantService.get(tenantId);
    }

    @PostMapping("/{tenantId}/suspend")
    @PreAuthorize(Roles.Has.PLATFORM_ADMIN)
    @Operation(summary = "Suspend an institution",
            description = "Blocks sign-in for all its users. Loan data is retained untouched.")
    public TenantResponse suspend(@PathVariable UUID tenantId,
                                  @Valid @RequestBody SuspendTenantRequest request) {
        return tenantService.suspend(tenantId, request.reason());
    }

    @PostMapping("/{tenantId}/activate")
    @PreAuthorize(Roles.Has.PLATFORM_ADMIN)
    @Operation(summary = "Reactivate a suspended institution")
    public TenantResponse activate(@PathVariable UUID tenantId) {
        return tenantService.activate(tenantId);
    }

    // ---------------------------------------------------------- the caller's institution

    @GetMapping("/current")
    @PreAuthorize(Roles.Has.READ)
    @Operation(summary = "Fetch your own institution's profile")
    public TenantResponse current() {
        return tenantService.currentTenant();
    }

    @PutMapping("/current")
    @PreAuthorize(Roles.Has.TENANT_ADMIN)
    @Operation(summary = "Update your institution's profile")
    public TenantResponse updateCurrent(@Valid @RequestBody UpdateTenantRequest request) {
        return tenantService.updateCurrent(request);
    }

    @PutMapping("/current/branding")
    @PreAuthorize(Roles.Has.TENANT_ADMIN)
    @Operation(summary = "Update logo and colours",
            description = "Requires a plan that includes custom branding.")
    public TenantResponse updateBranding(@Valid @RequestBody BrandingRequest request) {
        return tenantService.updateBranding(request);
    }

    @GetMapping("/current/subscription")
    @PreAuthorize(Roles.Has.TENANT_ADMIN)
    @Operation(summary = "Your current plan, limits and enabled features")
    public SubscriptionResponse subscription() {
        return tenantService.currentSubscription();
    }

    @GetMapping("/branding/{slug}")
    @SecurityRequirements
    @Operation(summary = "Public branding for a sign-in screen",
            description = "Unauthenticated: the app needs the logo and colours before anyone has "
                    + "signed in. Returns only information the institution publishes anyway.")
    public TenantBrandingResponse branding(@PathVariable String slug) {
        return tenantService.brandingBySlug(slug);
    }

    // ---------------------------------------------------------- branches and settings

    @PostMapping("/current/branches")
    @PreAuthorize(Roles.Has.TENANT_ADMIN)
    @Operation(summary = "Create a branch")
    public ResponseEntity<BranchResponse> createBranch(@Valid @RequestBody BranchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tenantService.createBranch(request));
    }

    @GetMapping("/current/branches")
    @PreAuthorize(Roles.Has.READ)
    @Operation(summary = "List branches")
    public List<BranchResponse> listBranches() {
        return tenantService.listBranches();
    }

    @PutMapping("/current/branches/{branchId}")
    @PreAuthorize(Roles.Has.TENANT_ADMIN)
    @Operation(summary = "Update a branch")
    public BranchResponse updateBranch(@PathVariable UUID branchId,
                                       @Valid @RequestBody BranchRequest request) {
        return tenantService.updateBranch(branchId, request);
    }

    @DeleteMapping("/current/branches/{branchId}")
    @PreAuthorize(Roles.Has.TENANT_ADMIN)
    @Operation(summary = "Deactivate a branch")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateBranch(@PathVariable UUID branchId) {
        tenantService.deactivateBranch(branchId);
    }

    @GetMapping("/current/settings")
    @PreAuthorize(Roles.Has.TENANT_ADMIN)
    @Operation(summary = "List institution settings")
    public List<SettingResponse> settings() {
        return tenantService.listSettings();
    }

    @PutMapping("/current/settings")
    @PreAuthorize(Roles.Has.TENANT_ADMIN)
    @Operation(summary = "Create or update a setting")
    public SettingResponse upsertSetting(@Valid @RequestBody SettingRequest request) {
        return tenantService.upsertSetting(request);
    }
}

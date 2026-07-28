package com.mfin.tenant.web;

import com.mfin.common.error.ApiExceptions;
import com.mfin.common.tenant.Roles;
import com.mfin.tenant.domain.SubscriptionPlan;
import com.mfin.tenant.repository.TenantRepositories.SubscriptionPlanRepository;
import com.mfin.tenant.web.dto.TenantDtos.PlanRequest;
import com.mfin.tenant.web.dto.TenantDtos.PlanResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/subscription-plans")
@Tag(name = "Subscription plans", description = "SaaS plans, limits and features")
public class SubscriptionPlanController {

    private final SubscriptionPlanRepository repository;

    public SubscriptionPlanController(SubscriptionPlanRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @Operation(summary = "List the plans available to subscribe to")
    public List<PlanResponse> list() {
        return repository.findByActiveTrueOrderByMonthlyPriceAsc().stream()
                .map(PlanResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize(Roles.Has.PLATFORM_ADMIN)
    @Operation(summary = "Create a subscription plan")
    @Transactional
    public ResponseEntity<PlanResponse> create(@Valid @RequestBody PlanRequest request) {
        repository.findByCodeIgnoreCase(request.code()).ifPresent(existing -> {
            throw new ApiExceptions.ConflictException(
                    "A plan with code '" + request.code() + "' already exists");
        });
        SubscriptionPlan plan = new SubscriptionPlan(request.code().toUpperCase(), request.name());
        plan.configure(request.name(), request.description(), request.monthlyPrice(),
                request.currency() == null ? "USD" : request.currency(), request.maxUsers(),
                request.maxCustomers(), request.maxActiveLoans(), request.maxBranches(),
                request.features() == null ? Set.of() : request.features());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PlanResponse.from(repository.save(plan)));
    }
}

package com.mfin.reporting.web;

import com.mfin.common.tenant.Roles;
import com.mfin.common.web.PageResponse;
import com.mfin.reporting.application.AuditService;
import com.mfin.reporting.application.ReportingService;
import com.mfin.reporting.web.ReportingDtos.ActivityReport;
import com.mfin.reporting.web.ReportingDtos.ArrearsAgingReport;
import com.mfin.reporting.web.ReportingDtos.AuditEntryResponse;
import com.mfin.reporting.web.ReportingDtos.ChainVerificationResult;
import com.mfin.reporting.web.ReportingDtos.DashboardSummary;
import com.mfin.reporting.web.ReportingDtos.PortfolioSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Reporting and audit", description = "Portfolio reports and the immutable audit trail")
public class ReportingController {

    private final ReportingService reportingService;
    private final AuditService auditService;

    public ReportingController(ReportingService reportingService, AuditService auditService) {
        this.reportingService = reportingService;
        this.auditService = auditService;
    }

    @GetMapping("/dashboard")
    @PreAuthorize(Roles.Has.READ)
    @Operation(summary = "Dashboard figures for the home screen")
    public DashboardSummary dashboard() {
        return reportingService.dashboard();
    }

    @GetMapping("/reports/portfolio")
    @PreAuthorize(Roles.Has.READ)
    @Operation(summary = "Portfolio summary",
            description = "Portfolio at risk is measured on outstanding principal, the definition "
                    + "used by regulators and funders.")
    public PortfolioSummary portfolio() {
        return reportingService.portfolioSummary();
    }

    @GetMapping("/reports/arrears-aging")
    @PreAuthorize(Roles.Has.READ)
    @Operation(summary = "Arrears aged into PAR buckets")
    public ArrearsAgingReport arrearsAging() {
        return reportingService.arrearsAging();
    }

    @GetMapping("/reports/activity")
    @PreAuthorize(Roles.Has.READ)
    @Operation(summary = "Disbursements and collections for a period",
            description = "Defaults to the current calendar month.")
    public ActivityReport activity(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportingService.activity(from, to);
    }

    @GetMapping("/audit")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','TENANT_ADMIN','AUDITOR')")
    @Operation(summary = "Search the audit trail",
            description = "Restricted to administrators and auditors. Entries are append-only and "
                    + "hash-chained.")
    public PageResponse<AuditEntryResponse> audit(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @ParameterObject @PageableDefault(size = 50) Pageable pageable) {
        return auditService.search(action, entityType, entityId, actorId, from, to, pageable);
    }

    @GetMapping("/audit/verify")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','TENANT_ADMIN','AUDITOR')")
    @Operation(summary = "Verify the audit hash chain",
            description = "Walks every entry and reports the first break, which is the signature "
                    + "of an altered or deleted record.")
    public ChainVerificationResult verify() {
        return auditService.verifyChain();
    }
}

package com.mfin.origination.web;

import com.mfin.common.tenant.Roles;
import com.mfin.common.web.PageResponse;
import com.mfin.loan.engine.LoanSchedule;
import com.mfin.origination.application.LoanApplicationService;
import com.mfin.origination.domain.LoanApplicationStatus;
import com.mfin.origination.web.dto.ApplicationDtos.ApplicationResponse;
import com.mfin.origination.web.dto.ApplicationDtos.ApplicationSummary;
import com.mfin.origination.web.dto.ApplicationDtos.ApproveRequest;
import com.mfin.origination.web.dto.ApplicationDtos.CreateApplicationRequest;
import com.mfin.origination.web.dto.ApplicationDtos.DecisionRequest;
import com.mfin.origination.web.dto.ApplicationDtos.DisburseRequest;
import com.mfin.origination.web.dto.ApplicationDtos.ReviewRequest;
import com.mfin.origination.web.dto.ApplicationDtos.UpdateApplicationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loan-applications")
@Tag(name = "Loan applications", description = "Origination, approval workflow and disbursement")
public class LoanApplicationController {

    private final LoanApplicationService applicationService;

    public LoanApplicationController(LoanApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    @PreAuthorize(Roles.Has.LOAN_WRITE)
    @Operation(summary = "Create a draft application",
            description = "Checks the customer is KYC-verified and the product is active before "
                    + "accepting the application.")
    public ResponseEntity<ApplicationResponse> create(
            @Valid @RequestBody CreateApplicationRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken) {
        ApplicationResponse created = applicationService.create(request, bearerToken);
        return ResponseEntity.created(URI.create("/api/v1/loan-applications/" + created.id()))
                .body(created);
    }

    @GetMapping
    @PreAuthorize(Roles.Has.READ)
    @Operation(summary = "Search applications",
            description = "Filter by status to build approval work queues.")
    public PageResponse<ApplicationSummary> search(
            @RequestParam(required = false) LoanApplicationStatus status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) String query,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return applicationService.search(status, customerId, branchId, query, pageable);
    }

    @GetMapping("/{applicationId}")
    @PreAuthorize(Roles.Has.READ)
    @Operation(summary = "Fetch an application with its approval history")
    public ApplicationResponse get(@PathVariable UUID applicationId) {
        return applicationService.get(applicationId);
    }

    @GetMapping("/{applicationId}/schedule")
    @PreAuthorize(Roles.Has.READ)
    @Operation(summary = "Preview the repayment schedule for these terms")
    public LoanSchedule previewSchedule(@PathVariable UUID applicationId,
                                        @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken) {
        return applicationService.previewSchedule(applicationId, bearerToken);
    }

    @PutMapping("/{applicationId}")
    @PreAuthorize(Roles.Has.LOAN_WRITE)
    @Operation(summary = "Edit a draft application")
    public ApplicationResponse update(@PathVariable UUID applicationId,
                                      @Valid @RequestBody UpdateApplicationRequest request,
                                      @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken) {
        return applicationService.update(applicationId, request, bearerToken);
    }

    @PostMapping("/{applicationId}/submit")
    @PreAuthorize(Roles.Has.LOAN_WRITE)
    @Operation(summary = "Submit an application for approval")
    public ApplicationResponse submit(@PathVariable UUID applicationId) {
        return applicationService.submit(applicationId);
    }

    @PostMapping("/{applicationId}/review")
    @PreAuthorize(Roles.Has.LOAN_APPROVE)
    @Operation(summary = "Take an application up for assessment")
    public ApplicationResponse review(@PathVariable UUID applicationId,
                                      @Valid @RequestBody ReviewRequest request) {
        return applicationService.beginReview(applicationId, request.comment());
    }

    @PostMapping("/{applicationId}/approve")
    @PreAuthorize(Roles.Has.LOAN_APPROVE)
    @Operation(summary = "Record an approval",
            description = """
                    Advances the application by one approval level. The loan is only APPROVED once
                    every level configured on the product has signed off, and no one person may
                    supply two of those approvals. An approver may sanction a reduced amount or
                    term.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Approval recorded"),
            @ApiResponse(responseCode = "422", description = "Illegal transition, duplicate approver, "
                    + "or terms outside product policy")
    })
    public ApplicationResponse approve(@PathVariable UUID applicationId,
                                       @Valid @RequestBody ApproveRequest request,
                                       @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken) {
        return applicationService.approve(applicationId, request, bearerToken);
    }

    @PostMapping("/{applicationId}/reject")
    @PreAuthorize(Roles.Has.LOAN_APPROVE)
    @Operation(summary = "Reject an application")
    public ApplicationResponse reject(@PathVariable UUID applicationId,
                                      @Valid @RequestBody DecisionRequest request) {
        return applicationService.reject(applicationId, request.reason());
    }

    @PostMapping("/{applicationId}/cancel")
    @PreAuthorize(Roles.Has.LOAN_WRITE)
    @Operation(summary = "Withdraw an application")
    public ApplicationResponse cancel(@PathVariable UUID applicationId,
                                      @Valid @RequestBody DecisionRequest request) {
        return applicationService.cancel(applicationId, request.reason());
    }

    @PostMapping("/{applicationId}/disburse")
    @PreAuthorize(Roles.Has.LOAN_APPROVE)
    @Operation(summary = "Record disbursement of an approved loan",
            description = """
                    Records the method, reference, value date and amount, then publishes
                    `loan.disbursed.v1` through the transactional outbox. The loan account service
                    consumes that event to open the account and generate the repayment schedule.
                    """)
    public ApplicationResponse disburse(@PathVariable UUID applicationId,
                                        @Valid @RequestBody DisburseRequest request,
                                        @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken) {
        return applicationService.disburse(applicationId, request, bearerToken);
    }
}

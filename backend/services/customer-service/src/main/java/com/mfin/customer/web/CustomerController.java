package com.mfin.customer.web;

import com.mfin.common.tenant.Roles;
import com.mfin.common.web.PageResponse;
import com.mfin.customer.application.CustomerService;
import com.mfin.customer.application.KycService;
import com.mfin.customer.domain.CustomerStatus;
import com.mfin.customer.domain.KycStatus;
import com.mfin.customer.web.dto.CustomerDtos.CreateCustomerRequest;
import com.mfin.customer.web.dto.CustomerDtos.CustomerEligibility;
import com.mfin.customer.web.dto.CustomerDtos.CustomerResponse;
import com.mfin.customer.web.dto.CustomerDtos.CustomerSummary;
import com.mfin.customer.web.dto.CustomerDtos.DeactivateCustomerRequest;
import com.mfin.customer.web.dto.CustomerDtos.DocumentResponse;
import com.mfin.customer.web.dto.CustomerDtos.UpdateCustomerRequest;
import com.mfin.customer.web.dto.CustomerDtos.UploadDocumentRequest;
import com.mfin.customer.web.dto.CustomerDtos.VerifyKycRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "Customers", description = "Customer registration, search and KYC")
public class CustomerController {

    private final CustomerService customerService;
    private final KycService kycService;

    public CustomerController(CustomerService customerService, KycService kycService) {
        this.customerService = customerService;
        this.kycService = kycService;
    }

    @PostMapping
    @PreAuthorize(Roles.Has.LOAN_WRITE)
    @Operation(summary = "Register a customer",
            description = "Identification numbers are encrypted at rest and only ever returned masked.")
    public ResponseEntity<CustomerResponse> register(
            @Valid @RequestBody CreateCustomerRequest request) {
        CustomerResponse created = customerService.register(request);
        return ResponseEntity.created(URI.create("/api/v1/customers/" + created.id())).body(created);
    }

    @GetMapping
    @PreAuthorize(Roles.Has.READ)
    @Operation(summary = "Search customers",
            description = """
                    Free-text matches names and customer number. A full phone number or
                    identification number also matches exactly, resolved through a keyed blind
                    index so the encrypted columns never have to be scanned in the clear.
                    """)
    public PageResponse<CustomerSummary> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) CustomerStatus status,
            @RequestParam(required = false) KycStatus kycStatus,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID loanOfficerId,
            @ParameterObject @PageableDefault(size = 20, sort = "lastName",
                    direction = Sort.Direction.ASC) Pageable pageable) {
        return customerService.search(query, status, kycStatus, branchId, loanOfficerId, pageable);
    }

    @GetMapping("/{customerId}")
    @PreAuthorize(Roles.Has.READ)
    @Operation(summary = "Fetch a customer profile")
    public CustomerResponse get(@PathVariable UUID customerId) {
        return customerService.get(customerId);
    }

    @GetMapping("/{customerId}/eligibility")
    @PreAuthorize(Roles.Has.READ)
    @Operation(summary = "Check lending eligibility",
            description = "Used by loan origination before an application is accepted.")
    public CustomerEligibility eligibility(@PathVariable UUID customerId) {
        return customerService.eligibility(customerId);
    }

    @PutMapping("/{customerId}")
    @PreAuthorize(Roles.Has.LOAN_WRITE)
    @Operation(summary = "Update a customer")
    public CustomerResponse update(@PathVariable UUID customerId,
                                   @Valid @RequestBody UpdateCustomerRequest request) {
        return customerService.update(customerId, request);
    }

    @PostMapping("/{customerId}/deactivate")
    @PreAuthorize(Roles.Has.MANAGEMENT)
    @Operation(summary = "Deactivate a customer",
            description = "Blocks new borrowing. Existing loans continue to be serviced and the "
                    + "record is retained for audit and statutory retention.")
    public CustomerResponse deactivate(@PathVariable UUID customerId,
                                       @Valid @RequestBody DeactivateCustomerRequest request) {
        return customerService.deactivate(customerId, request.reason());
    }

    @PostMapping("/{customerId}/reactivate")
    @PreAuthorize(Roles.Has.MANAGEMENT)
    @Operation(summary = "Reactivate a customer")
    public CustomerResponse reactivate(@PathVariable UUID customerId) {
        return customerService.reactivate(customerId);
    }

    // ------------------------------------------------------------------ KYC

    @PostMapping("/{customerId}/kyc/documents")
    @PreAuthorize(Roles.Has.LOAN_WRITE)
    @Operation(summary = "Attach a KYC document",
            description = "Takes the object-storage key of an already-uploaded file; the file "
                    + "itself never transits this service.")
    public ResponseEntity<DocumentResponse> uploadDocument(
            @PathVariable UUID customerId, @Valid @RequestBody UploadDocumentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(kycService.uploadDocument(customerId, request));
    }

    @GetMapping("/{customerId}/kyc/documents")
    @PreAuthorize(Roles.Has.READ)
    @Operation(summary = "List a customer's KYC documents")
    public List<DocumentResponse> listDocuments(@PathVariable UUID customerId) {
        return kycService.listDocuments(customerId);
    }

    @PostMapping("/{customerId}/kyc/documents/{documentId}/verify")
    @PreAuthorize(Roles.Has.MANAGEMENT)
    @Operation(summary = "Approve or reject a single document")
    public DocumentResponse verifyDocument(@PathVariable UUID customerId,
                                           @PathVariable UUID documentId,
                                           @Valid @RequestBody VerifyKycRequest request) {
        return kycService.verifyDocument(customerId, documentId, request);
    }

    @PostMapping("/{customerId}/kyc/decision")
    @PreAuthorize(Roles.Has.MANAGEMENT)
    @Operation(summary = "Record the overall KYC decision",
            description = "Approval requires every mandatory document to be verified and unexpired.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decideKyc(@PathVariable UUID customerId,
                          @Valid @RequestBody VerifyKycRequest request) {
        kycService.decideKyc(customerId, request);
    }
}

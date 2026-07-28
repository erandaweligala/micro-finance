package com.mfin.product.web;

import com.mfin.common.tenant.Roles;
import com.mfin.common.web.PageResponse;
import com.mfin.product.application.LoanCalculationService;
import com.mfin.product.application.LoanProductService;
import com.mfin.product.domain.ProductStatus;
import com.mfin.product.web.dto.CalculationDtos.CalculationRequest;
import com.mfin.product.web.dto.CalculationDtos.CalculationResponse;
import com.mfin.product.web.dto.ProductDtos.ProductRequest;
import com.mfin.product.web.dto.ProductDtos.ProductResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loan-products")
@Tag(name = "Loan products", description = "Product configuration and loan pricing")
public class LoanProductController {

    private final LoanProductService productService;
    private final LoanCalculationService calculationService;

    public LoanProductController(LoanProductService productService,
                                 LoanCalculationService calculationService) {
        this.productService = productService;
        this.calculationService = calculationService;
    }

    @PostMapping
    @PreAuthorize(Roles.Has.TENANT_ADMIN)
    @Operation(summary = "Create a loan product",
            description = "Created in DRAFT status; activate it before it can be used.")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = productService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/loan-products/" + created.id()))
                .body(created);
    }

    @GetMapping
    @PreAuthorize(Roles.Has.READ)
    @Operation(summary = "List loan products")
    public PageResponse<ProductResponse> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) ProductStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "name",
                    direction = Sort.Direction.ASC) Pageable pageable) {
        return productService.search(query, status, pageable);
    }

    @GetMapping("/{productId}")
    @PreAuthorize(Roles.Has.READ)
    @Operation(summary = "Fetch a loan product")
    public ProductResponse get(@PathVariable UUID productId) {
        return productService.get(productId);
    }

    @PutMapping("/{productId}")
    @PreAuthorize(Roles.Has.TENANT_ADMIN)
    @Operation(summary = "Update a loan product's configuration")
    public ProductResponse update(@PathVariable UUID productId,
                                  @Valid @RequestBody ProductRequest request) {
        return productService.update(productId, request);
    }

    @PostMapping("/{productId}/activate")
    @PreAuthorize(Roles.Has.TENANT_ADMIN)
    @Operation(summary = "Make a product available for new applications")
    public ProductResponse activate(@PathVariable UUID productId) {
        return productService.activate(productId);
    }

    @DeleteMapping("/{productId}")
    @PreAuthorize(Roles.Has.TENANT_ADMIN)
    @Operation(summary = "Withdraw a product from sale",
            description = "Existing loans continue to run to maturity.")
    public ProductResponse deactivate(@PathVariable UUID productId) {
        return productService.deactivate(productId);
    }

    @PostMapping("/{productId}/calculate")
    @PreAuthorize(Roles.Has.READ)
    @Operation(summary = "Quote a loan against this product",
            description = "Fills omitted terms from the product's defaults and enforces its policy "
                    + "bounds. Returns the full repayment schedule.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Schedule calculated"),
            @ApiResponse(responseCode = "422", description = "Terms breach product policy or are not amortisable")
    })
    public CalculationResponse calculateForProduct(@PathVariable UUID productId,
                                                   @Valid @RequestBody CalculationRequest request) {
        // The path is authoritative: a body carrying a different productId must not win.
        CalculationRequest scoped = new CalculationRequest(productId, request.principal(),
                request.annualInterestRate(), request.numberOfInstallments(),
                request.repaymentFrequency(), request.interestMethod(), request.disbursementDate(),
                request.firstRepaymentDate(), request.graceType(), request.gracePeriods(),
                request.feeType(), request.feeValue(), request.feeCollection(), request.dayCount(),
                request.currencyScale());
        return calculationService.calculate(scoped);
    }
}

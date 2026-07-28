package com.mfin.product.application;

import com.mfin.common.error.ApiExceptions;
import com.mfin.common.tenant.TenantContext;
import com.mfin.common.web.PageResponse;
import com.mfin.product.domain.LoanProduct;
import com.mfin.product.domain.ProductStatus;
import com.mfin.product.repository.LoanProductRepository;
import com.mfin.product.web.dto.ProductDtos.ProductRequest;
import com.mfin.product.web.dto.ProductDtos.ProductResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Product configuration. Writes are restricted to tenant administrators at the web layer. */
@Service
public class LoanProductService {

    private final LoanProductRepository repository;

    public LoanProductService(LoanProductRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        if (repository.existsByTenantIdAndCodeIgnoreCase(tenantId, request.code())) {
            throw new ApiExceptions.ConflictException(
                    "A product with code '" + request.code() + "' already exists");
        }
        LoanProduct product = new LoanProduct(request.code(), request.name(), request.currency());
        apply(product, request);
        // Products start as DRAFT and must be activated explicitly, so a half-configured
        // product can never be sold by accident.
        product.validateConfiguration();
        return ProductResponse.from(repository.save(product));
    }

    @Transactional
    public ProductResponse update(UUID productId, ProductRequest request) {
        LoanProduct product = require(productId);
        if (!product.getCode().equalsIgnoreCase(request.code())) {
            throw new ApiExceptions.BusinessRuleException(
                    "A product's code cannot be changed once created");
        }
        apply(product, request);
        product.validateConfiguration();
        return ProductResponse.from(repository.save(product));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String query, ProductStatus status, Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();
        String normalised = query == null || query.isBlank() ? null : query.trim();
        return PageResponse.from(repository.search(tenantId, status, normalised, pageable),
                ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID productId) {
        return ProductResponse.from(require(productId));
    }

    @Transactional
    public ProductResponse activate(UUID productId) {
        LoanProduct product = require(productId);
        product.activate();
        return ProductResponse.from(repository.save(product));
    }

    @Transactional
    public ProductResponse deactivate(UUID productId) {
        LoanProduct product = require(productId);
        product.deactivate();
        return ProductResponse.from(repository.save(product));
    }

    private LoanProduct require(UUID productId) {
        UUID tenantId = TenantContext.requireTenantId();
        return repository.findByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("Loan product", productId));
    }

    private void apply(LoanProduct product, ProductRequest request) {
        product.describe(request.name(), request.description(), request.currencyScale(),
                request.dayCount());
        product.configureAmounts(request.minPrincipal(), request.maxPrincipal(),
                request.defaultPrincipal());
        product.configureRates(request.minAnnualRate(), request.maxAnnualRate(),
                request.defaultAnnualRate(), request.rateQuotation(), request.interestMethod());
        product.configureTerm(request.repaymentFrequency(), request.minInstallments(),
                request.maxInstallments(), request.defaultInstallments(), request.graceType(),
                request.maxGracePeriods());
        product.configureFees(request.feeType(), request.feeValue(), request.feeCollection());
        product.configurePenalties(request.penaltyAnnualRate(), request.penaltyBasis(),
                request.penaltyGraceDays(), request.daysToDefault());
        product.configureWorkflow(request.approvalLevels(), request.requiresCollateral(),
                request.requiresGuarantor());
    }
}

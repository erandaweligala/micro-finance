package com.mfin.product.application;

import com.mfin.common.error.ApiExceptions;
import com.mfin.common.tenant.TenantContext;
import com.mfin.loan.engine.LoanCalculationRequest;
import com.mfin.loan.engine.LoanCalculator;
import com.mfin.loan.engine.LoanSchedule;
import com.mfin.product.domain.LoanProduct;
import com.mfin.product.repository.LoanProductRepository;
import com.mfin.product.web.dto.CalculationDtos.CalculationRequest;
import com.mfin.product.web.dto.CalculationDtos.CalculationResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Prices a loan.
 *
 * <p>Two modes, both landing in the same engine: a <em>quotation</em> against a configured
 * product, where omitted fields fall back to product defaults and the result is checked against
 * product policy, and a <em>free-form</em> calculation for the standalone calculator screen.
 * Keeping both here means the schedule a customer is quoted is produced by exactly the same
 * code that later generates the real one at disbursement.</p>
 */
@Service
public class LoanCalculationService {

    private final LoanProductRepository productRepository;
    private final LoanCalculator calculator;

    public LoanCalculationService(LoanProductRepository productRepository,
                                  LoanCalculator calculator) {
        this.productRepository = productRepository;
        this.calculator = calculator;
    }

    @Transactional(readOnly = true)
    public CalculationResponse calculate(CalculationRequest request) {
        LoanProduct product = request.productId() == null ? null : loadProduct(request.productId());
        LoanCalculationRequest engineRequest = toEngineRequest(request, product);

        if (product != null) {
            // A quotation that breaches policy must fail here rather than at submission,
            // so the officer sees it while they can still adjust the terms.
            product.validateTerms(engineRequest.principal(), engineRequest.annualInterestRate(),
                    engineRequest.numberOfInstallments(), engineRequest.gracePeriods());
        }

        LoanSchedule schedule = calculator.generate(engineRequest);
        return CalculationResponse.from(schedule, product == null ? null : product.getCurrency());
    }

    /**
     * Builds the engine request, filling gaps from the product where one was supplied.
     * Bean Validation has already bounded every field; the engine re-checks the combination.
     */
    private LoanCalculationRequest toEngineRequest(CalculationRequest request, LoanProduct product) {
        LoanCalculationRequest.Builder builder = LoanCalculationRequest.builder()
                .principal(request.principal())
                .annualInterestRate(request.annualInterestRate())
                .numberOfInstallments(request.numberOfInstallments())
                .frequency(request.repaymentFrequency())
                .interestMethod(request.interestMethod())
                .disbursementDate(request.disbursementDate() == null
                        ? LocalDate.now() : request.disbursementDate())
                .firstRepaymentDate(request.firstRepaymentDate())
                .gracePeriods(request.gracePeriods());

        if (request.graceType() != null) {
            builder.graceType(request.graceType());
        }
        if (request.currencyScale() != null) {
            builder.currencyScale(request.currencyScale());
        } else if (product != null) {
            builder.currencyScale(product.getCurrencyScale());
        }
        if (request.dayCount() != null) {
            builder.dayCount(request.dayCount());
        } else if (product != null) {
            builder.dayCount(product.getDayCount());
        }

        // Fees come from the product unless the caller overrides them explicitly.
        if (request.feeValue() != null) {
            builder.feeValue(request.feeValue())
                    .feeType(request.feeType())
                    .feeCollection(request.feeCollection());
        } else if (product != null) {
            builder.feeValue(product.getFeeValue())
                    .feeType(product.getFeeType())
                    .feeCollection(product.getFeeCollection());
        }
        return builder.build();
    }

    private LoanProduct loadProduct(UUID productId) {
        UUID tenantId = TenantContext.requireTenantId();
        return productRepository.findByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("Loan product", productId));
    }
}

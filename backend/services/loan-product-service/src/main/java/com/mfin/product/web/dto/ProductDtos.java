package com.mfin.product.web.dto;

import com.mfin.loan.engine.DayCountConvention;
import com.mfin.loan.engine.FeeCollection;
import com.mfin.loan.engine.FeeType;
import com.mfin.loan.engine.GraceType;
import com.mfin.loan.engine.InterestMethod;
import com.mfin.loan.engine.RepaymentFrequency;
import com.mfin.product.domain.LoanProduct;
import com.mfin.product.domain.PenaltyBasis;
import com.mfin.product.domain.ProductStatus;
import com.mfin.product.domain.RateQuotation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/** Loan product payloads. */
public final class ProductDtos {

    private ProductDtos() {
    }

    @Schema(description = "Create or replace a loan product's configuration")
    public record ProductRequest(
            @NotBlank @Size(max = 32)
            @Pattern(regexp = "^[A-Z0-9_-]+$", message = "Code must be upper-case letters, digits, _ or -")
            @Schema(example = "SME-WORKING-CAPITAL")
            String code,

            @NotBlank @Size(max = 128) String name,
            @Size(max = 512) String description,

            @NotBlank @Size(min = 3, max = 3)
            @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code")
            @Schema(example = "KES")
            String currency,

            @Min(0) @Max(4)
            @Schema(description = "Decimal places of the currency", example = "2")
            int currencyScale,

            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 15, fraction = 4)
            BigDecimal minPrincipal,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 15, fraction = 4)
            BigDecimal maxPrincipal,
            @Digits(integer = 15, fraction = 4) BigDecimal defaultPrincipal,

            @NotNull @DecimalMin("0.0") @DecimalMax("200.0") @Digits(integer = 5, fraction = 4)
            BigDecimal minAnnualRate,
            @NotNull @DecimalMin("0.0") @DecimalMax("200.0") @Digits(integer = 5, fraction = 4)
            BigDecimal maxAnnualRate,
            @NotNull @DecimalMin("0.0") @DecimalMax("200.0") @Digits(integer = 5, fraction = 4)
            BigDecimal defaultAnnualRate,

            @NotNull RateQuotation rateQuotation,
            @NotNull InterestMethod interestMethod,
            @NotNull RepaymentFrequency repaymentFrequency,

            @Min(1) @Max(600) int minInstallments,
            @Min(1) @Max(600) int maxInstallments,
            @Min(1) @Max(600) int defaultInstallments,

            @NotNull GraceType graceType,
            @Min(0) @Max(24) int maxGracePeriods,

            @NotNull FeeType feeType,
            @NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal feeValue,
            @NotNull FeeCollection feeCollection,
            @NotNull DayCountConvention dayCount,

            @NotNull @DecimalMin("0.0") @DecimalMax("200.0") @Digits(integer = 5, fraction = 4)
            BigDecimal penaltyAnnualRate,
            @NotNull PenaltyBasis penaltyBasis,
            @Min(0) @Max(90) int penaltyGraceDays,
            @Min(1) @Max(3650) int daysToDefault,

            @Min(1) @Max(5)
            @Schema(description = "Sequential approvals required before disbursement")
            int approvalLevels,
            boolean requiresCollateral,
            boolean requiresGuarantor
    ) {
    }

    @Schema(description = "A configured loan product")
    public record ProductResponse(
            UUID id,
            String code,
            String name,
            String description,
            String currency,
            int currencyScale,
            BigDecimal minPrincipal,
            BigDecimal maxPrincipal,
            BigDecimal defaultPrincipal,
            BigDecimal minAnnualRate,
            BigDecimal maxAnnualRate,
            BigDecimal defaultAnnualRate,
            RateQuotation rateQuotation,
            InterestMethod interestMethod,
            RepaymentFrequency repaymentFrequency,
            int minInstallments,
            int maxInstallments,
            int defaultInstallments,
            GraceType graceType,
            int maxGracePeriods,
            FeeType feeType,
            BigDecimal feeValue,
            FeeCollection feeCollection,
            DayCountConvention dayCount,
            BigDecimal penaltyAnnualRate,
            PenaltyBasis penaltyBasis,
            int penaltyGraceDays,
            int daysToDefault,
            int approvalLevels,
            boolean requiresCollateral,
            boolean requiresGuarantor,
            ProductStatus status
    ) {
        public static ProductResponse from(LoanProduct product) {
            return new ProductResponse(product.getId(), product.getCode(), product.getName(),
                    product.getDescription(), product.getCurrency(), product.getCurrencyScale(),
                    product.getMinPrincipal(), product.getMaxPrincipal(), product.getDefaultPrincipal(),
                    product.getMinAnnualRate(), product.getMaxAnnualRate(), product.getDefaultAnnualRate(),
                    product.getRateQuotation(), product.getInterestMethod(),
                    product.getRepaymentFrequency(), product.getMinInstallments(),
                    product.getMaxInstallments(), product.getDefaultInstallments(),
                    product.getGraceType(), product.getMaxGracePeriods(), product.getFeeType(),
                    product.getFeeValue(), product.getFeeCollection(), product.getDayCount(),
                    product.getPenaltyAnnualRate(), product.getPenaltyBasis(),
                    product.getPenaltyGraceDays(), product.getDaysToDefault(),
                    product.getApprovalLevels(), product.isRequiresCollateral(),
                    product.isRequiresGuarantor(), product.getStatus());
        }
    }
}

package com.mfin.product.web.dto;

import com.mfin.loan.engine.DayCountConvention;
import com.mfin.loan.engine.FeeCollection;
import com.mfin.loan.engine.FeeType;
import com.mfin.loan.engine.GraceType;
import com.mfin.loan.engine.InterestMethod;
import com.mfin.loan.engine.LoanSchedule;
import com.mfin.loan.engine.RepaymentFrequency;
import com.mfin.loan.engine.ScheduledInstallment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Loan calculator payloads - what the mobile calculator screen posts and renders. */
public final class CalculationDtos {

    private CalculationDtos() {
    }

    @Schema(description = """
            Loan calculation request. When productId is supplied the product's defaults fill in
            any omitted field and the resulting terms are validated against product policy;
            without it the figures are used as given, which is what the standalone calculator does.
            """)
    public record CalculationRequest(
            @Schema(description = "Optional product to price against") UUID productId,

            @NotNull(message = "Principal is required")
            @DecimalMin(value = "0.01", message = "Principal must be greater than zero")
            @Digits(integer = 15, fraction = 4)
            @Schema(example = "100000.00")
            BigDecimal principal,

            @NotNull(message = "Interest rate is required")
            @DecimalMin(value = "0.0", message = "Interest rate must not be negative")
            @DecimalMax(value = "200.0", message = "Interest rate must not exceed 200%")
            @Digits(integer = 5, fraction = 4)
            @Schema(description = "Nominal annual rate, in percent", example = "12.5")
            BigDecimal annualInterestRate,

            @Min(value = 1, message = "At least one installment is required")
            @Max(value = 600, message = "At most 600 installments are supported")
            @Schema(example = "12")
            int numberOfInstallments,

            @NotNull(message = "Repayment frequency is required")
            RepaymentFrequency repaymentFrequency,

            @NotNull(message = "Interest method is required")
            InterestMethod interestMethod,

            @Schema(description = "Defaults to today when omitted")
            LocalDate disbursementDate,

            @Schema(description = "Defaults to one full period after disbursement. A later date "
                    + "creates a broken first period and attracts stub interest.")
            LocalDate firstRepaymentDate,

            GraceType graceType,
            @Min(0) @Max(24) int gracePeriods,

            FeeType feeType,
            @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal feeValue,
            FeeCollection feeCollection,
            DayCountConvention dayCount,

            @Min(0) @Max(4) Integer currencyScale
    ) {
    }

    @Schema(description = "One row of the repayment schedule")
    public record InstallmentResponse(
            int installmentNumber,
            LocalDate dueDate,
            BigDecimal openingBalance,
            BigDecimal principal,
            BigDecimal interest,
            BigDecimal fee,
            BigDecimal totalDue,
            @Schema(description = "Principal still outstanding after this installment")
            BigDecimal closingBalance,
            BigDecimal cumulativePrincipal,
            BigDecimal cumulativeInterest,
            boolean graceInstallment
    ) {
        public static InstallmentResponse from(ScheduledInstallment row) {
            return new InstallmentResponse(row.installmentNumber(), row.dueDate(),
                    row.openingBalance(), row.principal(), row.interest(), row.fee(),
                    row.totalDue(), row.closingBalance(), row.cumulativePrincipal(),
                    row.cumulativeInterest(), row.graceInstallment());
        }
    }

    @Schema(description = "Calculated loan terms and the full repayment schedule")
    public record CalculationResponse(
            InterestMethod interestMethod,
            RepaymentFrequency repaymentFrequency,
            BigDecimal principal,
            @Schema(description = "The level installment; the final row may differ by a rounding residual")
            BigDecimal installmentAmount,
            BigDecimal totalInterest,
            BigDecimal totalFees,
            BigDecimal totalRepayable,
            @Schema(description = "Cash released after any fee deducted at disbursement")
            BigDecimal netDisbursedAmount,
            @Schema(description = "Periodic rate as a fraction, for disclosure", example = "0.010417")
            BigDecimal periodicRate,
            LocalDate firstDueDate,
            LocalDate maturityDate,
            @Schema(description = "Stub interest folded into the first installment")
            BigDecimal brokenPeriodInterest,
            String currency,
            List<InstallmentResponse> schedule
    ) {
        public static CalculationResponse from(LoanSchedule schedule, String currency) {
            return new CalculationResponse(
                    schedule.interestMethod(),
                    schedule.frequency(),
                    schedule.principal(),
                    schedule.regularInstallment(),
                    schedule.totalInterest(),
                    schedule.totalFees(),
                    schedule.totalRepayable(),
                    schedule.netDisbursedAmount(),
                    schedule.periodicRate(),
                    schedule.effectiveFirstDueDate(),
                    schedule.maturityDate(),
                    schedule.brokenPeriodInterest(),
                    currency,
                    schedule.installments().stream().map(InstallmentResponse::from).toList());
        }
    }
}

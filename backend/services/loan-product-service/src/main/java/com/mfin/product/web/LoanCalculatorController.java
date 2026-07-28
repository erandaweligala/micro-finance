package com.mfin.product.web;

import com.mfin.product.application.LoanCalculationService;
import com.mfin.product.web.dto.CalculationDtos.CalculationRequest;
import com.mfin.product.web.dto.CalculationDtos.CalculationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The standalone calculator behind the mobile app's calculator screen. Any authenticated user
 * may price a hypothetical loan; nothing is persisted.
 */
@RestController
@RequestMapping("/api/v1/loan-calculations")
@Tag(name = "Loan calculator", description = "Ad-hoc loan pricing")
public class LoanCalculatorController {

    private final LoanCalculationService calculationService;

    public LoanCalculatorController(LoanCalculationService calculationService) {
        this.calculationService = calculationService;
    }

    @PostMapping
    @Operation(summary = "Calculate a repayment schedule",
            description = """
                    Computes the installment, total interest, total repayable and the complete
                    amortisation schedule with the outstanding balance after each installment.

                    Reducing balance uses the annuity formula
                    `P*i*(1+i)^n / ((1+i)^n - 1)`; flat rate uses
                    `totalInterest = P * annualRate * years`. Zero-interest loans, grace periods,
                    broken first periods and non-monthly frequencies are all supported.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Schedule calculated"),
            @ApiResponse(responseCode = "400", description = "Request failed validation"),
            @ApiResponse(responseCode = "422", description = "Terms are not amortisable")
    })
    public CalculationResponse calculate(@Valid @RequestBody CalculationRequest request) {
        return calculationService.calculate(request);
    }
}

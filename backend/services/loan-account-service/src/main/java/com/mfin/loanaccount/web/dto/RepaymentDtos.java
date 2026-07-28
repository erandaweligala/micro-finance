package com.mfin.loanaccount.web.dto;

import com.mfin.loanaccount.domain.AllocationOrder;
import com.mfin.loanaccount.domain.LoanAccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Internal repayment posting payloads, used by the payment service. */
public final class RepaymentDtos {

    private RepaymentDtos() {
    }

    @Schema(description = "Apply a received payment to a loan. Idempotent on paymentId.")
    public record ApplyRepaymentRequest(
            @NotNull(message = "Payment id is required")
            @Schema(description = "The payment service's id for this receipt; replays are ignored")
            UUID paymentId,

            @NotNull @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
            @Digits(integer = 15, fraction = 4)
            BigDecimal amount,

            @NotNull(message = "Value date is required")
            @Schema(description = "Date the money is treated as received; decides which "
                    + "installments count as due")
            LocalDate valueDate,

            @Schema(description = "Overrides the institution's default allocation policy")
            AllocationOrder allocationOrder
    ) {
    }

    public record ReverseRepaymentRequest(
            @NotBlank(message = "A reason is required for a reversal") @Size(max = 512) String reason
    ) {
    }

    @Schema(description = "How a payment was split and where the loan stands afterwards")
    public record RepaymentResult(
            UUID paymentId,
            UUID loanAccountId,
            String accountNumber,
            @Schema(description = "Borrower the loan belongs to; authoritative, so the payment "
                    + "service does not have to trust a client-supplied customer id")
            UUID customerId,
            String currency,
            BigDecimal amount,
            BigDecimal penaltyAllocated,
            BigDecimal interestAllocated,
            BigDecimal feeAllocated,
            BigDecimal principalAllocated,
            @Schema(description = "Money beyond everything owed, held as a credit")
            BigDecimal excessAmount,
            BigDecimal outstandingPrincipalAfter,
            BigDecimal totalOutstandingAfter,
            LoanAccountStatus status,
            boolean reversed
    ) {
    }
}

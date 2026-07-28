package com.mfin.payment.web.dto;

import com.mfin.payment.domain.Payment;
import com.mfin.payment.domain.PaymentMethod;
import com.mfin.payment.domain.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Payment capture, reversal and receipt payloads. */
public final class PaymentDtos {

    private PaymentDtos() {
    }

    @Schema(description = """
            Record a repayment. Send an `Idempotency-Key` header: if the request is retried after
            a timeout the original receipt is returned rather than a second payment being taken.
            """)
    public record CapturePaymentRequest(
            @NotNull(message = "Loan account is required") UUID loanAccountId,

            @NotNull(message = "Amount is required")
            @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
            @Digits(integer = 15, fraction = 4)
            BigDecimal amount,

            @NotNull(message = "Payment method is required") PaymentMethod method,

            @Schema(description = "Channel transaction id, e.g. the mobile-money reference")
            @Size(max = 64) String externalReference,

            @NotNull(message = "Value date is required")
            @PastOrPresent(message = "A payment cannot be dated in the future")
            LocalDate valueDate,

            @Size(max = 512) String narrative
    ) {
    }

    public record ReversePaymentRequest(
            @NotBlank(message = "A reason is required to reverse a payment")
            @Size(max = 512) String reason
    ) {
    }

    @Schema(description = "A recorded payment with the split the loan account applied")
    public record PaymentResponse(
            UUID id,
            String receiptNumber,
            UUID loanAccountId,
            String loanAccountNumber,
            UUID customerId,
            BigDecimal amount,
            String currency,
            PaymentMethod method,
            String externalReference,
            LocalDate valueDate,
            String narrative,
            BigDecimal penaltyAllocated,
            BigDecimal feeAllocated,
            BigDecimal interestAllocated,
            BigDecimal principalAllocated,
            @Schema(description = "Amount beyond everything owed, held as a credit")
            BigDecimal excessAmount,
            BigDecimal outstandingPrincipalAfter,
            BigDecimal totalOutstandingAfter,
            PaymentStatus status,
            UUID receivedBy,
            Instant reversedAt,
            String reversalReason,
            Instant createdAt
    ) {
        public static PaymentResponse from(Payment payment) {
            return new PaymentResponse(payment.getId(), payment.getReceiptNumber(),
                    payment.getLoanAccountId(), payment.getLoanAccountNumber(),
                    payment.getCustomerId(), payment.getAmount(), payment.getCurrency(),
                    payment.getMethod(), payment.getExternalReference(), payment.getValueDate(),
                    payment.getNarrative(), payment.getPenaltyAllocated(), payment.getFeeAllocated(),
                    payment.getInterestAllocated(), payment.getPrincipalAllocated(),
                    payment.getExcessAmount(), payment.getOutstandingPrincipalAfter(),
                    payment.getTotalOutstandingAfter(), payment.getStatus(), payment.getReceivedBy(),
                    payment.getReversedAt(), payment.getReversalReason(), payment.getCreatedAt());
        }
    }

    @Schema(description = "Printable receipt for a successful payment")
    public record ReceiptResponse(
            String receiptNumber,
            Instant issuedAt,
            String institutionName,
            UUID customerId,
            String loanAccountNumber,
            BigDecimal amount,
            String currency,
            PaymentMethod method,
            String externalReference,
            LocalDate valueDate,
            BigDecimal penaltyAllocated,
            BigDecimal feeAllocated,
            BigDecimal interestAllocated,
            BigDecimal principalAllocated,
            BigDecimal excessAmount,
            BigDecimal balanceAfter,
            String receivedByName,
            boolean reversed
    ) {
        public static ReceiptResponse from(Payment payment, String institutionName,
                                           String receivedByName) {
            return new ReceiptResponse(payment.getReceiptNumber(), payment.getCreatedAt(),
                    institutionName, payment.getCustomerId(), payment.getLoanAccountNumber(),
                    payment.getAmount(), payment.getCurrency(), payment.getMethod(),
                    payment.getExternalReference(), payment.getValueDate(),
                    payment.getPenaltyAllocated(), payment.getFeeAllocated(),
                    payment.getInterestAllocated(), payment.getPrincipalAllocated(),
                    payment.getExcessAmount(), payment.getTotalOutstandingAfter(),
                    receivedByName, payment.isReversed());
        }
    }
}

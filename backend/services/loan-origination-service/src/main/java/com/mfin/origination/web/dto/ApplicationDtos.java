package com.mfin.origination.web.dto;

import com.mfin.loan.engine.GraceType;
import com.mfin.loan.engine.InterestMethod;
import com.mfin.loan.engine.RepaymentFrequency;
import com.mfin.origination.domain.ApprovalDecision;
import com.mfin.origination.domain.ApprovalRecord;
import com.mfin.origination.domain.DisbursementMethod;
import com.mfin.origination.domain.LoanApplication;
import com.mfin.origination.domain.LoanApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Loan application payloads. */
public final class ApplicationDtos {

    private ApplicationDtos() {
    }

    @Schema(description = "Create a draft loan application")
    public record CreateApplicationRequest(
            @NotNull(message = "Customer is required") UUID customerId,
            @NotNull(message = "Loan product is required") UUID productId,

            @NotNull @DecimalMin(value = "0.01", message = "Requested amount must be greater than zero")
            @Digits(integer = 15, fraction = 4)
            BigDecimal requestedAmount,

            @Min(1) @Max(600) int requestedInstallments,

            @Schema(description = "Defaults to the product's rate when omitted")
            @DecimalMin("0.0") @Digits(integer = 5, fraction = 4)
            BigDecimal annualInterestRate,

            @Schema(description = "Defaults to the product's method when omitted")
            InterestMethod interestMethod,
            RepaymentFrequency repaymentFrequency,
            GraceType graceType,
            @Min(0) @Max(24) int gracePeriods,

            @Size(max = 512) String purpose,
            @FutureOrPresent(message = "Expected disbursement date cannot be in the past")
            LocalDate expectedDisbursementDate,
            LocalDate firstRepaymentDate
    ) {
    }

    public record UpdateApplicationRequest(
            @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 4) BigDecimal requestedAmount,
            @Min(1) @Max(600) int requestedInstallments,
            @DecimalMin("0.0") @Digits(integer = 5, fraction = 4) BigDecimal annualInterestRate,
            InterestMethod interestMethod,
            RepaymentFrequency repaymentFrequency,
            GraceType graceType,
            @Min(0) @Max(24) int gracePeriods,
            @Size(max = 512) String purpose,
            LocalDate expectedDisbursementDate,
            LocalDate firstRepaymentDate
    ) {
    }

    @Schema(description = "Approve an application, optionally on reduced terms")
    public record ApproveRequest(
            @Size(max = 1024) String comment,

            @Schema(description = "Sanctioned amount when it differs from the request")
            @DecimalMin("0.01") @Digits(integer = 15, fraction = 4)
            BigDecimal approvedAmount,

            @Schema(description = "Sanctioned installment count when it differs from the request")
            @Min(1) @Max(600) Integer approvedInstallments
    ) {
    }

    public record DecisionRequest(
            @NotBlank(message = "A reason is required") @Size(max = 512) String reason
    ) {
    }

    public record ReviewRequest(
            @Size(max = 1024) String comment
    ) {
    }

    @Schema(description = "Record the release of funds for an approved loan")
    public record DisburseRequest(
            @NotNull(message = "Disbursement method is required") DisbursementMethod method,

            @NotBlank(message = "A disbursement reference is required")
            @Size(max = 64)
            @Schema(description = "Bank/mobile-money reference or cash receipt number",
                    example = "MPESA-QGH7X2P1")
            String reference,

            @NotNull(message = "Disbursement date is required")
            @Schema(description = "Value date the funds were released")
            LocalDate disbursementDate,

            @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 4)
            @Schema(description = "Must equal the approved amount")
            BigDecimal amount
    ) {
    }

    public record ApprovalResponse(
            UUID id,
            int approvalLevel,
            ApprovalDecision decision,
            UUID decidedBy,
            String comment,
            BigDecimal approvedAmount,
            Integer approvedInstallments,
            Instant decidedAt
    ) {
        public static ApprovalResponse from(ApprovalRecord record) {
            return new ApprovalResponse(record.getId(), record.getApprovalLevel(),
                    record.getDecision(), record.getDecidedBy(), record.getComment(),
                    record.getApprovedAmount(), record.getApprovedInstallments(),
                    record.getCreatedAt());
        }
    }

    @Schema(description = "A loan application with its full approval history")
    public record ApplicationResponse(
            UUID id,
            String applicationNumber,
            UUID customerId,
            String customerName,
            UUID productId,
            String productName,
            String currency,
            BigDecimal requestedAmount,
            int requestedInstallments,
            BigDecimal approvedAmount,
            Integer approvedInstallments,
            BigDecimal annualInterestRate,
            InterestMethod interestMethod,
            RepaymentFrequency repaymentFrequency,
            GraceType graceType,
            int gracePeriods,
            String purpose,
            LocalDate expectedDisbursementDate,
            LocalDate firstRepaymentDate,
            LoanApplicationStatus status,
            int currentApprovalLevel,
            int requiredApprovalLevels,
            UUID branchId,
            UUID loanOfficerId,
            Instant submittedAt,
            Instant decidedAt,
            String rejectionReason,
            LocalDate disbursementDate,
            DisbursementMethod disbursementMethod,
            String disbursementReference,
            BigDecimal disbursedAmount,
            BigDecimal netDisbursedAmount,
            List<ApprovalResponse> approvals,
            Instant createdAt
    ) {
        public static ApplicationResponse from(LoanApplication application) {
            return new ApplicationResponse(application.getId(), application.getApplicationNumber(),
                    application.getCustomerId(), application.getCustomerName(),
                    application.getProductId(), application.getProductName(),
                    application.getCurrency(), application.getRequestedAmount(),
                    application.getRequestedInstallments(), application.getApprovedAmount(),
                    application.getApprovedInstallments(), application.getAnnualInterestRate(),
                    application.getInterestMethod(), application.getRepaymentFrequency(),
                    application.getGraceType(), application.getGracePeriods(),
                    application.getPurpose(), application.getExpectedDisbursementDate(),
                    application.getFirstRepaymentDate(), application.getStatus(),
                    application.getCurrentApprovalLevel(), application.getRequiredApprovalLevels(),
                    application.getBranchId(), application.getLoanOfficerId(),
                    application.getSubmittedAt(), application.getDecidedAt(),
                    application.getRejectionReason(), application.getDisbursementDate(),
                    application.getDisbursementMethod(), application.getDisbursementReference(),
                    application.getDisbursedAmount(), application.getNetDisbursedAmount(),
                    application.getApprovals().stream().map(ApprovalResponse::from).toList(),
                    application.getCreatedAt());
        }
    }

    @Schema(description = "Compact application record for work queues")
    public record ApplicationSummary(
            UUID id,
            String applicationNumber,
            UUID customerId,
            String customerName,
            String productName,
            BigDecimal requestedAmount,
            BigDecimal approvedAmount,
            LoanApplicationStatus status,
            int currentApprovalLevel,
            int requiredApprovalLevels,
            Instant submittedAt,
            Instant createdAt
    ) {
        public static ApplicationSummary from(LoanApplication application) {
            return new ApplicationSummary(application.getId(), application.getApplicationNumber(),
                    application.getCustomerId(), application.getCustomerName(),
                    application.getProductName(), application.getRequestedAmount(),
                    application.getApprovedAmount(), application.getStatus(),
                    application.getCurrentApprovalLevel(), application.getRequiredApprovalLevels(),
                    application.getSubmittedAt(), application.getCreatedAt());
        }
    }
}

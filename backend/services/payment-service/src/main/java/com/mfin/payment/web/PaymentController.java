package com.mfin.payment.web;

import com.mfin.common.tenant.Roles;
import com.mfin.common.web.ApiHeaders;
import com.mfin.common.web.PageResponse;
import com.mfin.payment.application.PaymentService;
import com.mfin.payment.domain.PaymentStatus;
import com.mfin.payment.web.dto.PaymentDtos.CapturePaymentRequest;
import com.mfin.payment.web.dto.PaymentDtos.PaymentResponse;
import com.mfin.payment.web.dto.PaymentDtos.ReceiptResponse;
import com.mfin.payment.web.dto.PaymentDtos.ReversePaymentRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@Validated
@Tag(name = "Payments", description = "Repayment capture, reversal and receipts")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @PreAuthorize(Roles.Has.PAYMENT_WRITE)
    @Operation(summary = "Record a repayment",
            description = """
                    Applies the payment to the loan, allocating it across penalties, fees,
                    interest and principal according to the institution's policy, and issues a
                    receipt.

                    **Always send an `Idempotency-Key`.** If the request is retried - a dropped
                    mobile connection, a double tap, a gateway timeout - the original receipt is
                    returned and the customer is not charged twice.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Payment recorded"),
            @ApiResponse(responseCode = "409", description = "The idempotency key was reused with a "
                    + "different payload"),
            @ApiResponse(responseCode = "422", description = "The loan cannot accept this payment"),
            @ApiResponse(responseCode = "503", description = "The loan account service is unavailable; "
                    + "no receipt was issued and the request may be retried")
    })
    public ResponseEntity<PaymentResponse> capture(
            @Parameter(description = "Client-generated key that makes this request replay-safe",
                    required = true, example = "9f1c2b7a-3d4e-4f10-9a2b-6c8d0e1f2a3b")
            @RequestHeader(ApiHeaders.IDEMPOTENCY_KEY) @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody CapturePaymentRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken) {
        PaymentResponse payment = paymentService.capture(idempotencyKey, request, bearerToken);
        return ResponseEntity.created(URI.create("/api/v1/payments/" + payment.id())).body(payment);
    }

    @GetMapping
    @PreAuthorize(Roles.Has.READ)
    @Operation(summary = "Search payments")
    public PageResponse<PaymentResponse> search(
            @RequestParam(required = false) UUID loanAccountId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String query,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return paymentService.search(loanAccountId, customerId, status, from, to, query, pageable);
    }

    @GetMapping("/{paymentId}")
    @PreAuthorize(Roles.Has.READ)
    @Operation(summary = "Fetch a payment")
    public PaymentResponse get(@PathVariable UUID paymentId) {
        return paymentService.get(paymentId);
    }

    @GetMapping("/{paymentId}/receipt")
    @PreAuthorize(Roles.Has.READ)
    @Operation(summary = "Fetch the receipt for a payment",
            description = "Reprintable at any time; the allocation is stored with the payment.")
    public ReceiptResponse receipt(@PathVariable UUID paymentId) {
        return paymentService.receipt(paymentId);
    }

    @PostMapping("/{paymentId}/reverse")
    @PreAuthorize(Roles.Has.PAYMENT_REVERSE)
    @Operation(summary = "Reverse a payment",
            description = """
                    Requires a manager's authority and a reason. The loan is restored to its
                    pre-payment state and the original receipt remains on the customer's ledger
                    marked REVERSED - financial history is never deleted.
                    """)
    public PaymentResponse reverse(@PathVariable UUID paymentId,
                                   @Valid @RequestBody ReversePaymentRequest request,
                                   @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken) {
        return paymentService.reverse(paymentId, request.reason(), bearerToken);
    }
}

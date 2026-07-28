package com.mfin.loanaccount.web;

import com.mfin.loanaccount.application.RepaymentService;
import com.mfin.loanaccount.web.dto.RepaymentDtos.ApplyRepaymentRequest;
import com.mfin.loanaccount.web.dto.RepaymentDtos.RepaymentResult;
import com.mfin.loanaccount.web.dto.RepaymentDtos.ReverseRepaymentRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Repayment posting, called by the payment service.
 *
 * <p>Under {@code /internal} because it is service-to-service: the gateway does not route this
 * prefix from the public internet, and the endpoints additionally require the SYSTEM role or a
 * cashier's own token. Balances are moved here rather than in the payment service because this
 * service owns them.</p>
 */
@RestController
@RequestMapping("/internal/v1/loan-accounts")
@Tag(name = "Repayments (internal)", description = "Service-to-service repayment posting")
public class InternalRepaymentController {

    private final RepaymentService repaymentService;

    public InternalRepaymentController(RepaymentService repaymentService) {
        this.repaymentService = repaymentService;
    }

    @PostMapping("/{accountId}/repayments")
    @PreAuthorize("hasAnyRole('SYSTEM','TENANT_ADMIN','BRANCH_MANAGER','CASHIER')")
    @Operation(summary = "Apply a repayment to a loan",
            description = """
                    Allocates the payment across the schedule - oldest due installment first, and
                    within each installment in the institution's configured bucket order - then
                    moves the loan's balances. Idempotent on paymentId, so a retry replays the
                    original result instead of posting twice.
                    """)
    public RepaymentResult apply(@PathVariable UUID accountId,
                                 @Valid @RequestBody ApplyRepaymentRequest request) {
        return repaymentService.apply(accountId, request);
    }

    @PostMapping("/{accountId}/repayments/{paymentId}/reverse")
    @PreAuthorize("hasAnyRole('SYSTEM','TENANT_ADMIN','BRANCH_MANAGER')")
    @Operation(summary = "Reverse a previously applied repayment",
            description = "Replays the original allocation in reverse so the loan returns exactly "
                    + "to its pre-payment state.")
    public RepaymentResult reverse(@PathVariable UUID accountId,
                                   @PathVariable UUID paymentId,
                                   @Valid @RequestBody ReverseRepaymentRequest request) {
        return repaymentService.reverse(accountId, paymentId, request);
    }
}

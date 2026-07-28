package com.mfin.loanaccount.web;

import com.mfin.common.tenant.Roles;
import com.mfin.common.web.PageResponse;
import com.mfin.loanaccount.application.LoanAccountService;
import com.mfin.loanaccount.domain.LoanAccountStatus;
import com.mfin.loanaccount.web.dto.LoanAccountDtos.AccountResponse;
import com.mfin.loanaccount.web.dto.LoanAccountDtos.AccountSummary;
import com.mfin.loanaccount.web.dto.LoanAccountDtos.InstallmentResponse;
import com.mfin.loanaccount.web.dto.LoanAccountDtos.PayoffQuote;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loan-accounts")
@Tag(name = "Loan accounts", description = "Live loans, repayment schedules and balances")
public class LoanAccountController {

    private final LoanAccountService loanAccountService;

    public LoanAccountController(LoanAccountService loanAccountService) {
        this.loanAccountService = loanAccountService;
    }

    @GetMapping
    @PreAuthorize(Roles.Has.READ)
    @Operation(summary = "Search the loan portfolio")
    public PageResponse<AccountSummary> search(
            @RequestParam(required = false) LoanAccountStatus status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) String query,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return loanAccountService.search(status, customerId, branchId, query, pageable);
    }

    @GetMapping("/{accountId}")
    @PreAuthorize(Roles.Has.READ)
    @Operation(summary = "Fetch a loan with its current balances")
    public AccountResponse get(@PathVariable UUID accountId) {
        return loanAccountService.get(accountId);
    }

    @GetMapping("/{accountId}/schedule")
    @PreAuthorize(Roles.Has.READ)
    @Operation(summary = "The repayment schedule",
            description = "Each row shows what is due and what has been paid per bucket, plus the "
                    + "outstanding balance after that installment.")
    public PageResponse<InstallmentResponse> schedule(
            @PathVariable UUID accountId,
            @ParameterObject @PageableDefault(size = 60) Pageable pageable) {
        return loanAccountService.schedule(accountId, pageable);
    }

    @GetMapping("/{accountId}/payoff-quote")
    @PreAuthorize(Roles.Has.READ)
    @Operation(summary = "Cost of settling the loan early",
            description = "Outstanding principal plus interest and charges accrued to the quote "
                    + "date; future scheduled interest is not charged.")
    public PayoffQuote payoffQuote(
            @PathVariable UUID accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return loanAccountService.payoffQuote(accountId, asOf);
    }
}

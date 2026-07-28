package com.mfin.ledger.web;

import com.mfin.common.tenant.Roles;
import com.mfin.common.web.PageResponse;
import com.mfin.ledger.application.LedgerService;
import com.mfin.ledger.domain.LedgerTransactionType;
import com.mfin.ledger.web.dto.LedgerDtos.LedgerEntryResponse;
import com.mfin.ledger.web.dto.LedgerDtos.StatementResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ledger")
@Tag(name = "Ledger", description = "Customer loan ledger and statements")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/entries")
    @PreAuthorize(Roles.Has.READ)
    @Operation(summary = "Query ledger entries",
            description = "Filter by loan, customer, transaction type and date range. Entries are "
                    + "returned oldest first so a running balance reads naturally.")
    public PageResponse<LedgerEntryResponse> entries(
            @RequestParam(required = false) UUID loanAccountId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) LedgerTransactionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @ParameterObject @PageableDefault(size = 50) Pageable pageable) {
        return ledgerService.search(loanAccountId, customerId, type, from, to, pageable);
    }

    @GetMapping("/loans/{loanAccountId}/statement")
    @PreAuthorize(Roles.Has.READ)
    @Operation(summary = "Customer statement for a loan",
            description = "Reconciles opening balance, debits, credits and closing balance for "
                    + "the requested period.")
    public StatementResponse statement(
            @PathVariable UUID loanAccountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ledgerService.statement(loanAccountId, from, to);
    }

    @GetMapping(value = "/loans/{loanAccountId}/statement.csv", produces = "text/csv")
    @PreAuthorize(Roles.Has.READ)
    @Operation(summary = "Export a statement as CSV")
    public ResponseEntity<byte[]> exportStatement(
            @PathVariable UUID loanAccountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        String csv = ledgerService.exportStatementCsv(loanAccountId, from, to);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"statement-" + loanAccountId + ".csv\"")
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}

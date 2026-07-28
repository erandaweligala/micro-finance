package com.mfin.ledger.web.dto;

import com.mfin.ledger.domain.LedgerEntry;
import com.mfin.ledger.domain.LedgerTransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Ledger and statement payloads. */
public final class LedgerDtos {

    private LedgerDtos() {
    }

    @Schema(description = "One line of the customer loan ledger")
    public record LedgerEntryResponse(
            UUID id,
            LocalDate transactionDate,
            String transactionReference,
            LedgerTransactionType transactionType,
            String narrative,
            @Schema(description = "Increases the amount owed") BigDecimal debitAmount,
            @Schema(description = "Reduces the amount owed") BigDecimal creditAmount,
            BigDecimal principalAllocation,
            BigDecimal interestAllocation,
            BigDecimal feeAllocation,
            BigDecimal penaltyAllocation,
            BigDecimal outstandingPrincipal,
            BigDecimal totalOutstanding,
            String currency,
            @Schema(description = "Set when this line reverses an earlier one")
            UUID reversesEntryId,
            Instant postedAt
    ) {
        public static LedgerEntryResponse from(LedgerEntry entry) {
            return new LedgerEntryResponse(entry.getId(), entry.getTransactionDate(),
                    entry.getTransactionReference(), entry.getTransactionType(),
                    entry.getNarrative(), entry.getDebitAmount(), entry.getCreditAmount(),
                    entry.getPrincipalAllocation(), entry.getInterestAllocation(),
                    entry.getFeeAllocation(), entry.getPenaltyAllocation(),
                    entry.getOutstandingPrincipal(), entry.getTotalOutstanding(),
                    entry.getCurrency(), entry.getReversesEntryId(), entry.getPostedAt());
        }
    }

    @Schema(description = "A customer statement for a period, reconciling opening to closing balance")
    public record StatementResponse(
            UUID loanAccountId,
            String loanAccountNumber,
            UUID customerId,
            LocalDate periodStart,
            LocalDate periodEnd,
            @Schema(description = "Net of everything posted before the period")
            BigDecimal openingBalance,
            BigDecimal totalDebits,
            BigDecimal totalCredits,
            @Schema(description = "openingBalance + debits - credits")
            BigDecimal closingBalance,
            @Schema(description = "Outstanding balance as at the last entry in the period")
            BigDecimal closingOutstanding,
            List<LedgerEntryResponse> entries
    ) {
    }
}

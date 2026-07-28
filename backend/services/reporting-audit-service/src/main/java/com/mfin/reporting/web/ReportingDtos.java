package com.mfin.reporting.web;

import com.mfin.reporting.domain.AuditLog;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ReportingDtos {

    private ReportingDtos() {
    }

    @Schema(description = "One immutable audit entry")
    public record AuditEntryResponse(
            UUID id,
            long sequenceNumber,
            String action,
            String entityType,
            UUID entityId,
            UUID actorId,
            String actorName,
            String details,
            Instant occurredAt,
            @Schema(description = "SHA-256 linking this entry to the previous one")
            String entryHash
    ) {
        public static AuditEntryResponse from(AuditLog entry) {
            return new AuditEntryResponse(entry.getId(), entry.getSequenceNumber(),
                    entry.getAction(), entry.getEntityType(), entry.getEntityId(),
                    entry.getActorId(), entry.getActorName(), entry.getDetails(),
                    entry.getOccurredAt(), entry.getEntryHash());
        }
    }

    @Schema(description = "Result of walking the audit hash chain")
    public record ChainVerificationResult(
            boolean intact,
            long entriesChecked,
            @Schema(description = "Sequence number where the chain first breaks, if it does")
            Long brokenAtSequence,
            String message
    ) {
    }

    @Schema(description = "Portfolio position at a point in time")
    public record PortfolioSummary(
            LocalDate asOf,
            long activeLoans,
            long overdueLoans,
            long closedLoans,
            BigDecimal outstandingPrincipal,
            BigDecimal totalCollected,
            @Schema(description = "Outstanding principal on loans more than 30 days past due")
            BigDecimal principalAtRisk30,
            @Schema(description = "Portfolio at risk over 30 days, as a percentage")
            BigDecimal par30Percentage
    ) {
    }

    @Schema(description = "Arrears aged into standard portfolio-at-risk buckets")
    public record ArrearsAgingReport(
            LocalDate asOf,
            @Schema(description = "Bucket name to number of loans")
            Map<String, Long> loanCounts,
            @Schema(description = "Bucket name to outstanding principal")
            Map<String, BigDecimal> outstandingPrincipal,
            BigDecimal totalOutstanding
    ) {
    }

    @Schema(description = "Disbursement and collection totals for a period")
    public record ActivityReport(
            LocalDate from,
            LocalDate to,
            BigDecimal totalDisbursed,
            BigDecimal totalCollected,
            long loansDisbursed
    ) {
    }

    @Schema(description = "Role-aware dashboard figures for the mobile home screen")
    public record DashboardSummary(
            long activeLoans,
            long overdueLoans,
            BigDecimal outstandingPrincipal,
            BigDecimal principalAtRisk30,
            BigDecimal collectedThisMonth,
            BigDecimal disbursedThisMonth,
            List<String> alerts
    ) {
    }
}

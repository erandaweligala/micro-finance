package com.mfin.reporting.application;

import com.mfin.common.tenant.TenantContext;
import com.mfin.reporting.domain.LoanSnapshot;
import com.mfin.reporting.repository.ReportingRepositories.LoanSnapshotRepository;
import com.mfin.reporting.web.ReportingDtos.ActivityReport;
import com.mfin.reporting.web.ReportingDtos.ArrearsAgingReport;
import com.mfin.reporting.web.ReportingDtos.DashboardSummary;
import com.mfin.reporting.web.ReportingDtos.PortfolioSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Portfolio reporting, served from this service's own read model.
 *
 * <p>Portfolio at risk is reported on <em>outstanding principal</em>, not on the number of
 * loans or the amount in arrears: that is the definition regulators and funders use, and
 * reporting anything else under the same name would mislead.</p>
 */
@Service
public class ReportingService {

    private final LoanSnapshotRepository snapshotRepository;

    public ReportingService(LoanSnapshotRepository snapshotRepository) {
        this.snapshotRepository = snapshotRepository;
    }

    @Transactional(readOnly = true)
    public PortfolioSummary portfolioSummary() {
        UUID tenantId = TenantContext.requireTenantId();
        BigDecimal outstanding = snapshotRepository.totalOutstandingPrincipal(tenantId);
        BigDecimal atRisk = snapshotRepository.principalAtRisk(tenantId, 30);

        return new PortfolioSummary(
                LocalDate.now(),
                snapshotRepository.countByStatus(tenantId, "ACTIVE"),
                snapshotRepository.countByStatus(tenantId, "OVERDUE"),
                snapshotRepository.countByStatus(tenantId, "CLOSED"),
                outstanding,
                snapshotRepository.totalCollected(tenantId),
                atRisk,
                percentage(atRisk, outstanding));
    }

    @Transactional(readOnly = true)
    public ArrearsAgingReport arrearsAging() {
        UUID tenantId = TenantContext.requireTenantId();
        List<LoanSnapshot> snapshots = snapshotRepository.findByTenantId(tenantId);

        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, BigDecimal> principal = new LinkedHashMap<>();
        for (String bucket : List.of("CURRENT", "PAR_1_30", "PAR_31_60", "PAR_61_90", "PAR_90_PLUS")) {
            counts.put(bucket, 0L);
            principal.put(bucket, BigDecimal.ZERO);
        }

        BigDecimal total = BigDecimal.ZERO;
        for (LoanSnapshot snapshot : snapshots) {
            if ("CLOSED".equals(snapshot.getStatus())) {
                continue;
            }
            String bucket = snapshot.arrearsBucket();
            counts.merge(bucket, 1L, Long::sum);
            principal.merge(bucket, snapshot.getOutstandingPrincipal(), BigDecimal::add);
            total = total.add(snapshot.getOutstandingPrincipal());
        }
        return new ArrearsAgingReport(LocalDate.now(), counts, principal, total);
    }

    @Transactional(readOnly = true)
    public ActivityReport activity(LocalDate from, LocalDate to) {
        UUID tenantId = TenantContext.requireTenantId();
        LocalDate start = from == null ? LocalDate.now().withDayOfMonth(1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;

        List<LoanSnapshot> snapshots = snapshotRepository.findByTenantId(tenantId);
        long disbursedCount = snapshots.stream()
                .filter(snapshot -> snapshot.getDisbursementDate() != null
                        && !snapshot.getDisbursementDate().isBefore(start)
                        && !snapshot.getDisbursementDate().isAfter(end))
                .count();

        return new ActivityReport(start, end,
                snapshotRepository.disbursedBetween(tenantId, start, end),
                snapshotRepository.totalCollected(tenantId),
                disbursedCount);
    }

    @Transactional(readOnly = true)
    public DashboardSummary dashboard() {
        UUID tenantId = TenantContext.requireTenantId();
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);

        BigDecimal outstanding = snapshotRepository.totalOutstandingPrincipal(tenantId);
        BigDecimal atRisk = snapshotRepository.principalAtRisk(tenantId, 30);
        long overdue = snapshotRepository.countByStatus(tenantId, "OVERDUE");

        List<String> alerts = new ArrayList<>();
        if (overdue > 0) {
            alerts.add(overdue + " loan(s) are in arrears");
        }
        BigDecimal par30 = percentage(atRisk, outstanding);
        if (par30.compareTo(BigDecimal.valueOf(5)) > 0) {
            // 5% PAR30 is the threshold most microfinance funders treat as a warning sign.
            alerts.add("Portfolio at risk over 30 days is " + par30 + "%");
        }

        return new DashboardSummary(
                snapshotRepository.countByStatus(tenantId, "ACTIVE"),
                overdue,
                outstanding,
                atRisk,
                snapshotRepository.totalCollected(tenantId),
                snapshotRepository.disbursedBetween(tenantId, monthStart, LocalDate.now()),
                alerts);
    }

    private BigDecimal percentage(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return part.multiply(BigDecimal.valueOf(100))
                .divide(whole, 2, RoundingMode.HALF_UP);
    }
}

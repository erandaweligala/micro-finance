package com.mfin.reporting.application;

import com.mfin.common.events.LoanEvents;
import com.mfin.common.events.PaymentEvents;
import com.mfin.reporting.domain.LoanSnapshot;
import com.mfin.reporting.repository.ReportingRepositories.LoanSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;

/** Maintains the reporting read model from loan and payment events. */
@Service
public class SnapshotService {

    private final LoanSnapshotRepository repository;

    public SnapshotService(LoanSnapshotRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void onLoanOpened(LoanEvents.LoanAccountOpened event) {
        LoanSnapshot snapshot = repository
                .findByTenantIdAndLoanAccountId(event.tenantId(), event.loanAccountId())
                .orElseGet(() -> {
                    LoanSnapshot created = new LoanSnapshot(event.loanAccountId(),
                            event.accountNumber(), event.customerId());
                    created.setTenantId(event.tenantId());
                    return created;
                });
        snapshot.onDisbursed(event.principal(), event.totalRepayable(),
                event.occurredAt().atZone(ZoneOffset.UTC).toLocalDate(), event.maturityDate());
        repository.save(snapshot);
    }

    @Transactional
    public void onPayment(PaymentEvents.PaymentPosted event) {
        repository.findByTenantIdAndLoanAccountId(event.tenantId(), event.loanAccountId())
                .ifPresent(snapshot -> {
                    snapshot.onPayment(event.amount(), event.outstandingPrincipalAfter(),
                            event.totalOutstandingAfter(), event.valueDate());
                    repository.save(snapshot);
                });
    }

    @Transactional
    public void onOverdue(LoanEvents.LoanOverdue event) {
        repository.findByTenantIdAndLoanAccountId(event.tenantId(), event.loanAccountId())
                .ifPresent(snapshot -> {
                    snapshot.onOverdue(event.daysPastDue());
                    repository.save(snapshot);
                });
    }
}

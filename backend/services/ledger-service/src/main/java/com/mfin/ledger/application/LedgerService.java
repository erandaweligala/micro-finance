package com.mfin.ledger.application;

import com.mfin.common.events.LoanEvents;
import com.mfin.common.events.PaymentEvents;
import com.mfin.common.tenant.TenantContext;
import com.mfin.common.web.PageResponse;
import com.mfin.ledger.domain.LedgerEntry;
import com.mfin.ledger.domain.LedgerTransactionType;
import com.mfin.ledger.repository.LedgerEntryRepository;
import com.mfin.ledger.web.dto.LedgerDtos.LedgerEntryResponse;
import com.mfin.ledger.web.dto.LedgerDtos.StatementResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Maintains the customer loan ledger from domain events.
 *
 * <p>The ledger is a projection: it never originates a transaction, it records the ones other
 * services report. Every write is guarded by the source event id, because Kafka delivery is
 * at-least-once and a duplicated disbursement line would misstate a customer's debt.</p>
 */
@Service
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final LedgerEntryRepository repository;

    public LedgerService(LedgerEntryRepository repository) {
        this.repository = repository;
    }

    /** Posts the opening debit when a loan is disbursed. */
    @Transactional
    public void recordDisbursement(LoanEvents.LoanAccountOpened event) {
        if (alreadyPosted(event.tenantId(), event.eventId())) {
            return;
        }
        LedgerEntry entry = new LedgerEntry(event.loanAccountId(), event.customerId(),
                event.occurredAt().atZone(java.time.ZoneOffset.UTC).toLocalDate(),
                event.accountNumber(), LedgerTransactionType.DISBURSEMENT, "KES",
                event.eventId(), event.eventType())
                .debit(event.principal())
                .withBalances(event.principal(), event.totalRepayable())
                .withAccountNumber(event.accountNumber())
                .withNarrative("Loan disbursed");
        entry.setTenantId(event.tenantId());
        repository.save(entry);
        log.info("Ledger: disbursement posted for loan {}", event.accountNumber());
    }

    /** Posts the credit for a repayment, carrying the allocation the loan account applied. */
    @Transactional
    public void recordPayment(PaymentEvents.PaymentPosted event) {
        if (alreadyPosted(event.tenantId(), event.eventId())) {
            return;
        }
        LedgerEntry entry = new LedgerEntry(event.loanAccountId(), event.customerId(),
                event.valueDate(), event.receiptNumber(), LedgerTransactionType.REPAYMENT,
                "KES", event.eventId(), event.eventType())
                .credit(event.amount())
                .withAllocation(event.principalAllocated(), event.interestAllocated(),
                        BigDecimal.ZERO, event.penaltyAllocated())
                .withBalances(event.outstandingPrincipalAfter(), event.totalOutstandingAfter())
                .withNarrative("Repayment received via " + event.paymentMethod());
        entry.setTenantId(event.tenantId());
        repository.save(entry);
    }

    /**
     * Posts the contra entry for a reversed payment.
     *
     * <p>A debit that mirrors the original credit, linked to it - the original line stays
     * exactly where it was. Deleting it would leave the customer's statement inconsistent with
     * the receipt they hold.</p>
     */
    @Transactional
    public void recordReversal(PaymentEvents.PaymentReversed event) {
        if (alreadyPosted(event.tenantId(), event.eventId())) {
            return;
        }
        UUID originalEntryId = repository
                .findByTenantIdAndSourceEventId(event.tenantId(), event.reversalOfPaymentId())
                .map(LedgerEntry::getId)
                .orElse(null);

        LedgerEntry entry = new LedgerEntry(event.loanAccountId(), null,
                event.occurredAt().atZone(java.time.ZoneOffset.UTC).toLocalDate(),
                "REV-" + event.reversalOfPaymentId(), LedgerTransactionType.REPAYMENT_REVERSAL,
                "KES", event.eventId(), event.eventType())
                .debit(event.amount())
                .reversing(originalEntryId)
                .withNarrative("Payment reversed: " + event.reason());
        entry.setTenantId(event.tenantId());
        repository.save(entry);
        log.warn("Ledger: reversal posted for loan {} amount {}", event.loanAccountId(), event.amount());
    }

    @Transactional(readOnly = true)
    public PageResponse<LedgerEntryResponse> search(UUID loanAccountId, UUID customerId,
                                                    LedgerTransactionType type, LocalDate from,
                                                    LocalDate to, Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();
        return PageResponse.from(repository.search(tenantId, loanAccountId, customerId, type,
                from, to, pageable), LedgerEntryResponse::from);
    }

    /**
     * Builds a customer statement for a period.
     *
     * <p>The opening balance is the net of everything posted <em>before</em> the period, so a
     * statement for any window reconciles with the one before it.</p>
     */
    @Transactional(readOnly = true)
    public StatementResponse statement(UUID loanAccountId, LocalDate from, LocalDate to) {
        UUID tenantId = TenantContext.requireTenantId();
        LocalDate periodStart = from == null ? LocalDate.of(1970, 1, 1) : from;
        LocalDate periodEnd = to == null ? LocalDate.now() : to;

        BigDecimal openingBalance = repository.totalDebitsBefore(tenantId, loanAccountId, periodStart)
                .subtract(repository.totalCreditsBefore(tenantId, loanAccountId, periodStart));

        List<LedgerEntry> entries =
                repository.findForStatement(tenantId, loanAccountId, periodStart, periodEnd);

        BigDecimal debits = entries.stream().map(LedgerEntry::getDebitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = entries.stream().map(LedgerEntry::getCreditAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String accountNumber = entries.isEmpty() ? null : entries.get(0).getLoanAccountNumber();
        UUID customerId = entries.isEmpty() ? null : entries.get(0).getCustomerId();
        BigDecimal closingOutstanding = entries.isEmpty()
                ? openingBalance
                : entries.get(entries.size() - 1).getTotalOutstanding();

        return new StatementResponse(loanAccountId, accountNumber, customerId, periodStart,
                periodEnd, openingBalance, debits, credits, openingBalance.add(debits).subtract(credits),
                closingOutstanding, entries.stream().map(LedgerEntryResponse::from).toList());
    }

    /** Renders a statement as CSV for download or emailing. */
    @Transactional(readOnly = true)
    public String exportStatementCsv(UUID loanAccountId, LocalDate from, LocalDate to) {
        StatementResponse statement = statement(loanAccountId, from, to);
        StringBuilder csv = new StringBuilder();
        csv.append("Date,Reference,Type,Narrative,Debit,Credit,Principal,Interest,Fee,Penalty,")
                .append("Outstanding Principal,Total Outstanding\n");
        for (LedgerEntryResponse entry : statement.entries()) {
            csv.append(entry.transactionDate()).append(',')
                    .append(escape(entry.transactionReference())).append(',')
                    .append(entry.transactionType()).append(',')
                    .append(escape(entry.narrative())).append(',')
                    .append(entry.debitAmount()).append(',')
                    .append(entry.creditAmount()).append(',')
                    .append(entry.principalAllocation()).append(',')
                    .append(entry.interestAllocation()).append(',')
                    .append(entry.feeAllocation()).append(',')
                    .append(entry.penaltyAllocation()).append(',')
                    .append(entry.outstandingPrincipal()).append(',')
                    .append(entry.totalOutstanding()).append('\n');
        }
        return csv.toString();
    }

    /**
     * Quotes a CSV field. Also neutralises values starting with a formula character, which
     * spreadsheet software would otherwise execute on open (CSV injection).
     */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        String sanitised = value;
        if (!sanitised.isEmpty() && "=+-@\t\r".indexOf(sanitised.charAt(0)) >= 0) {
            sanitised = "'" + sanitised;
        }
        return '"' + sanitised.replace("\"", "\"\"") + '"';
    }

    private boolean alreadyPosted(UUID tenantId, UUID eventId) {
        if (repository.existsByTenantIdAndSourceEventId(tenantId, eventId)) {
            log.debug("Ledger entry for event {} already exists; ignoring redelivery", eventId);
            return true;
        }
        return false;
    }
}

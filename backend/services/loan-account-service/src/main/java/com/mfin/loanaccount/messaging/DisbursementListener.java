package com.mfin.loanaccount.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mfin.common.events.LoanEvents;
import com.mfin.common.events.Topics;
import com.mfin.common.outbox.DomainEventPublisher;
import com.mfin.common.tenant.TenantContext;
import com.mfin.common.tenant.TenantPrincipal;
import com.mfin.loanaccount.application.LoanAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Opens a loan account when origination reports a disbursement.
 *
 * <p>This is the second leg of the disbursement saga. Three things make it safe:</p>
 * <ul>
 *   <li>Offsets are committed manually and only after the transaction succeeds, so a crash
 *       mid-processing replays the event rather than losing it.</li>
 *   <li>Account opening is idempotent on the application id, so a replay is harmless.</li>
 *   <li>A failure that cannot be retried publishes {@code loan.account-opening-failed.v1}, which
 *       brings a human to a disbursed loan with no account instead of leaving it silent.</li>
 * </ul>
 */
@Component
public class DisbursementListener {

    private static final Logger log = LoggerFactory.getLogger(DisbursementListener.class);

    private final ObjectMapper objectMapper;
    private final LoanAccountService loanAccountService;
    private final DomainEventPublisher eventPublisher;

    public DisbursementListener(ObjectMapper objectMapper,
                                LoanAccountService loanAccountService,
                                DomainEventPublisher eventPublisher) {
        this.objectMapper = objectMapper;
        this.loanAccountService = loanAccountService;
        this.eventPublisher = eventPublisher;
    }

    @KafkaListener(topics = Topics.LOAN_APPLICATION, groupId = "loan-account-service")
    @Transactional
    public void onLoanApplicationEvent(@Payload String payload,
                                       @Header(name = "eventType", required = false) String eventType,
                                       Acknowledgment acknowledgment) {
        try {
            if (!LoanEvents.LoanDisbursed.TYPE.equals(eventType)) {
                // Other events on this topic are not ours; acknowledge and move on.
                acknowledgment.acknowledge();
                return;
            }
            LoanEvents.LoanDisbursed event =
                    objectMapper.readValue(payload, LoanEvents.LoanDisbursed.class);

            // Consumers run outside any request, so the tenant is bound explicitly from the
            // event before touching tenant-scoped tables.
            TenantContext.runAs(TenantPrincipal.system(event.tenantId()),
                    () -> loanAccountService.openFromDisbursement(event));

            acknowledgment.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to open a loan account from a disbursement event", ex);
            // Not acknowledged: the broker redelivers, and the container's back-off and
            // dead-letter policy takes over once the retries are exhausted.
            throw new IllegalStateException("Unable to process disbursement event", ex);
        }
    }

    /**
     * Publishes the compensating event when account opening has definitively failed.
     * Invoked by the dead-letter handler.
     */
    @KafkaListener(topics = Topics.LOAN_APPLICATION + Topics.DLT_SUFFIX,
            groupId = "loan-account-service-dlt")
    @Transactional
    public void onDeadLetter(@Payload String payload,
                             @Header(name = "eventType", required = false) String eventType,
                             Acknowledgment acknowledgment) {
        try {
            if (LoanEvents.LoanDisbursed.TYPE.equals(eventType)) {
                LoanEvents.LoanDisbursed event =
                        objectMapper.readValue(payload, LoanEvents.LoanDisbursed.class);
                TenantContext.runAs(TenantPrincipal.system(event.tenantId()), () ->
                        eventPublisher.publish(new LoanEvents.LoanAccountOpeningFailed(
                                UUID.randomUUID(), event.tenantId(), event.applicationId(),
                                "Loan account could not be opened after repeated attempts",
                                Instant.now())));
                log.error("Disbursed application {} has no loan account; compensation published",
                        event.applicationId());
            }
        } catch (Exception ex) {
            log.error("Unable to handle a dead-lettered disbursement event", ex);
        } finally {
            acknowledgment.acknowledge();
        }
    }
}

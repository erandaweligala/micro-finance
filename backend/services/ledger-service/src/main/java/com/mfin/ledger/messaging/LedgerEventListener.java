package com.mfin.ledger.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mfin.common.events.LoanEvents;
import com.mfin.common.events.PaymentEvents;
import com.mfin.common.events.Topics;
import com.mfin.common.tenant.TenantContext;
import com.mfin.common.tenant.TenantPrincipal;
import com.mfin.ledger.application.LedgerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Builds the ledger from the events other services publish.
 *
 * <p>Offsets are acknowledged only after the entry is committed, and every write is guarded by
 * the source event id, so redelivery is harmless and a crash mid-batch replays rather than
 * skips.</p>
 */
@Component
public class LedgerEventListener {

    private static final Logger log = LoggerFactory.getLogger(LedgerEventListener.class);

    private final ObjectMapper objectMapper;
    private final LedgerService ledgerService;

    public LedgerEventListener(ObjectMapper objectMapper, LedgerService ledgerService) {
        this.objectMapper = objectMapper;
        this.ledgerService = ledgerService;
    }

    @KafkaListener(topics = Topics.LOAN_ACCOUNT, groupId = "ledger-service")
    public void onLoanAccountEvent(@Payload String payload,
                                   @Header(name = "eventType", required = false) String eventType,
                                   Acknowledgment acknowledgment) throws Exception {
        if (LoanEvents.LoanAccountOpened.TYPE.equals(eventType)) {
            var event = objectMapper.readValue(payload, LoanEvents.LoanAccountOpened.class);
            TenantContext.runAs(TenantPrincipal.system(event.tenantId()),
                    () -> ledgerService.recordDisbursement(event));
        }
        acknowledgment.acknowledge();
    }

    @KafkaListener(topics = Topics.PAYMENT, groupId = "ledger-service")
    public void onPaymentEvent(@Payload String payload,
                               @Header(name = "eventType", required = false) String eventType,
                               Acknowledgment acknowledgment) throws Exception {
        if (PaymentEvents.PaymentPosted.TYPE.equals(eventType)) {
            var event = objectMapper.readValue(payload, PaymentEvents.PaymentPosted.class);
            TenantContext.runAs(TenantPrincipal.system(event.tenantId()),
                    () -> ledgerService.recordPayment(event));
        } else if (PaymentEvents.PaymentReversed.TYPE.equals(eventType)) {
            var event = objectMapper.readValue(payload, PaymentEvents.PaymentReversed.class);
            TenantContext.runAs(TenantPrincipal.system(event.tenantId()),
                    () -> ledgerService.recordReversal(event));
        } else {
            log.debug("Ignoring unrelated payment-topic event {}", eventType);
        }
        acknowledgment.acknowledge();
    }
}

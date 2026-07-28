package com.mfin.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mfin.common.events.LoanEvents;
import com.mfin.common.events.NotificationEvents;
import com.mfin.common.events.PaymentEvents;
import com.mfin.common.events.Topics;
import com.mfin.common.tenant.TenantContext;
import com.mfin.common.tenant.TenantPrincipal;
import com.mfin.notification.application.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Turns domain events into borrower communications.
 *
 * <p>Deliberately tolerant: an unparseable or unexpected event is acknowledged and logged rather
 * than blocking the partition. A missed reminder is regrettable; a stalled consumer that also
 * stops overdue notices is worse.</p>
 */
@Component
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    public NotificationListener(ObjectMapper objectMapper, NotificationService notificationService) {
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
    }

    /** Explicit requests from other services, e.g. a password reset. */
    @KafkaListener(topics = Topics.NOTIFICATION, groupId = "notification-service")
    public void onNotificationRequested(@Payload String payload,
                                        @Header(name = "eventType", required = false) String eventType,
                                        Acknowledgment acknowledgment) {
        try {
            if (NotificationEvents.NotificationRequested.TYPE.equals(eventType)) {
                var event = objectMapper.readValue(payload,
                        NotificationEvents.NotificationRequested.class);
                runAs(event.tenantId(), () -> notificationService.handle(event));
            }
        } catch (Exception ex) {
            log.error("Unable to handle a notification request", ex);
        } finally {
            acknowledgment.acknowledge();
        }
    }

    @KafkaListener(topics = Topics.PAYMENT, groupId = "notification-service")
    public void onPayment(@Payload String payload,
                          @Header(name = "eventType", required = false) String eventType,
                          Acknowledgment acknowledgment) {
        try {
            if (PaymentEvents.PaymentPosted.TYPE.equals(eventType)) {
                var event = objectMapper.readValue(payload, PaymentEvents.PaymentPosted.class);
                var request = new NotificationEvents.NotificationRequested(
                        event.eventId(), event.tenantId(), NotificationEvents.Channel.SMS,
                        "PAYMENT_RECEIVED", customerContact(event.customerId()),
                        Map.of("receiptNumber", event.receiptNumber(),
                                "amount", String.valueOf(event.amount()),
                                "balance", String.valueOf(event.totalOutstandingAfter())),
                        event.loanAccountId(), Instant.now());
                runAs(event.tenantId(), () -> notificationService.handle(request));
            }
        } catch (Exception ex) {
            log.error("Unable to send a payment notification", ex);
        } finally {
            acknowledgment.acknowledge();
        }
    }

    @KafkaListener(topics = Topics.LOAN_ACCOUNT, groupId = "notification-service")
    public void onLoanAccount(@Payload String payload,
                              @Header(name = "eventType", required = false) String eventType,
                              Acknowledgment acknowledgment) {
        try {
            if (LoanEvents.LoanOverdue.TYPE.equals(eventType)) {
                var event = objectMapper.readValue(payload, LoanEvents.LoanOverdue.class);
                var request = new NotificationEvents.NotificationRequested(
                        event.eventId(), event.tenantId(), NotificationEvents.Channel.SMS,
                        "LOAN_OVERDUE", customerContact(event.customerId()),
                        Map.of("daysPastDue", String.valueOf(event.daysPastDue()),
                                "overdueAmount", String.valueOf(event.overdueAmount())),
                        event.loanAccountId(), Instant.now());
                runAs(event.tenantId(), () -> notificationService.handle(request));
            } else if (LoanEvents.LoanAccountOpened.TYPE.equals(eventType)) {
                var event = objectMapper.readValue(payload, LoanEvents.LoanAccountOpened.class);
                var request = new NotificationEvents.NotificationRequested(
                        event.eventId(), event.tenantId(), NotificationEvents.Channel.SMS,
                        "LOAN_DISBURSED", customerContact(event.customerId()),
                        Map.of("accountNumber", event.accountNumber(),
                                "principal", String.valueOf(event.principal()),
                                "maturityDate", String.valueOf(event.maturityDate())),
                        event.loanAccountId(), Instant.now());
                runAs(event.tenantId(), () -> notificationService.handle(request));
            }
        } catch (Exception ex) {
            log.error("Unable to send a loan notification", ex);
        } finally {
            acknowledgment.acknowledge();
        }
    }

    private void runAs(UUID tenantId, Runnable work) {
        TenantContext.runAs(TenantPrincipal.system(tenantId), work);
    }

    /**
     * Resolves the borrower's contact address.
     *
     * <p>Customer contact details are deliberately absent from events - replicating PII across
     * topics multiplies the places it must be protected and erased. A production deployment
     * resolves it here through the customer service's contact endpoint, which is access-logged.
     * Until that lookup is wired, the message is recorded against the customer id and the
     * transport treats it as undeliverable rather than sending to a fabricated address.</p>
     */
    private String customerContact(UUID customerId) {
        return "customer:" + customerId;
    }
}

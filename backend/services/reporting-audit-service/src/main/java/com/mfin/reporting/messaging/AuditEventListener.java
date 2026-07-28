package com.mfin.reporting.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mfin.common.events.LoanEvents;
import com.mfin.common.events.PaymentEvents;
import com.mfin.common.events.Topics;
import com.mfin.common.tenant.TenantContext;
import com.mfin.common.tenant.TenantPrincipal;
import com.mfin.reporting.application.AuditService;
import com.mfin.reporting.application.SnapshotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Consumes every business topic, writing the audit trail and maintaining the reporting read
 * model.
 *
 * <p>Auditing is derived from events rather than from HTTP interception, which means an action
 * is recorded because it <em>happened</em>, not because someone remembered to annotate the
 * controller that caused it.</p>
 */
@Component
public class AuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuditEventListener.class);

    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final SnapshotService snapshotService;

    public AuditEventListener(ObjectMapper objectMapper, AuditService auditService,
                              SnapshotService snapshotService) {
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.snapshotService = snapshotService;
    }

    @KafkaListener(topics = {Topics.CUSTOMER, Topics.LOAN_APPLICATION, Topics.TENANT},
            groupId = "reporting-audit-service")
    public void onBusinessEvent(@Payload String payload,
                                @Header(name = "eventType", required = false) String eventType,
                                Acknowledgment acknowledgment) {
        try {
            recordAudit(payload, eventType);
        } catch (Exception ex) {
            log.error("Unable to record an audit entry for event {}", eventType, ex);
        } finally {
            acknowledgment.acknowledge();
        }
    }

    @KafkaListener(topics = {Topics.LOAN_ACCOUNT, Topics.PAYMENT},
            groupId = "reporting-audit-service")
    public void onFinancialEvent(@Payload String payload,
                                 @Header(name = "eventType", required = false) String eventType,
                                 Acknowledgment acknowledgment) {
        try {
            recordAudit(payload, eventType);
            updateSnapshot(payload, eventType);
        } catch (Exception ex) {
            log.error("Unable to process financial event {}", eventType, ex);
        } finally {
            acknowledgment.acknowledge();
        }
    }

    /**
     * Writes the audit entry generically from the event envelope, so a new event type is
     * audited without changing this class.
     */
    private void recordAudit(String payload, String eventType) throws Exception {
        JsonNode node = objectMapper.readTree(payload);
        UUID tenantId = uuid(node, "tenantId");
        if (tenantId == null || eventType == null) {
            return;
        }
        TenantContext.runAs(TenantPrincipal.system(tenantId), () ->
                auditService.record(tenantId, eventType, node.path("aggregateType").asText("Unknown"),
                        uuid(node, "aggregateId"), uuid(node, "approvedBy"), null,
                        redact(node).toString(), uuid(node, "eventId"), Instant.now()));
    }

    private void updateSnapshot(String payload, String eventType) throws Exception {
        if (LoanEvents.LoanAccountOpened.TYPE.equals(eventType)) {
            var event = objectMapper.readValue(payload, LoanEvents.LoanAccountOpened.class);
            TenantContext.runAs(TenantPrincipal.system(event.tenantId()),
                    () -> snapshotService.onLoanOpened(event));
        } else if (PaymentEvents.PaymentPosted.TYPE.equals(eventType)) {
            var event = objectMapper.readValue(payload, PaymentEvents.PaymentPosted.class);
            TenantContext.runAs(TenantPrincipal.system(event.tenantId()),
                    () -> snapshotService.onPayment(event));
        } else if (LoanEvents.LoanOverdue.TYPE.equals(eventType)) {
            var event = objectMapper.readValue(payload, LoanEvents.LoanOverdue.class);
            TenantContext.runAs(TenantPrincipal.system(event.tenantId()),
                    () -> snapshotService.onOverdue(event));
        }
    }

    /**
     * Strips anything that must not be persisted into an audit record. Audit trails are read by
     * more people than the systems that produced them.
     */
    private JsonNode redact(JsonNode node) {
        if (node.isObject()) {
            com.fasterxml.jackson.databind.node.ObjectNode copy = node.deepCopy();
            copy.remove(java.util.List.of("resetToken", "password", "temporaryPassword",
                    "nationalId", "phoneNumber", "email", "variables"));
            return copy;
        }
        return node;
    }

    private UUID uuid(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.asText());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}

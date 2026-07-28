package com.mfin.common.outbox;

import com.mfin.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A domain event staged in the same database transaction as the state change that produced it.
 *
 * <p>This is what makes "the loan was disbursed" and "a LoanDisbursed event exists" atomic:
 * publishing straight to Kafka inside a transaction can succeed while the transaction rolls
 * back (a phantom event) or fail after commit (a lost event). Writing to this table commits
 * with the business data; {@link OutboxRelay} delivers it afterwards, at least once.</p>
 *
 * <p>Not a {@code TenantAwareEntity}: the relay runs outside any request and must be able to
 * drain every tenant's events, so the tenant id is carried as a plain column and copied onto
 * the message headers instead.</p>
 */
@Entity
@Table(name = "outbox_event", indexes = {
        @Index(name = "ix_outbox_status_created", columnList = "status, created_at"),
        @Index(name = "ix_outbox_aggregate", columnList = "aggregate_type, aggregate_id")
})
public class OutboxEvent extends BaseEntity {

    public enum Status {
        /** Awaiting delivery. */
        PENDING,
        /** Acknowledged by the broker. */
        PUBLISHED,
        /** Exhausted its retries; requires operator attention. Alerted on. */
        FAILED
    }

    @Column(name = "tenant_id", columnDefinition = "CHAR(36)")
    private UUID tenantId;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 96)
    private String eventType;

    @Column(name = "topic", nullable = false, length = 128)
    private String topic;

    /** Serialised event body. JSON, so consumers can evolve independently. */
    @Column(name = "payload", nullable = false, columnDefinition = "JSON")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error", length = 512)
    private String lastError;

    protected OutboxEvent() {
    }

    public OutboxEvent(UUID tenantId, String aggregateType, UUID aggregateId,
                       String eventType, String topic, String payload) {
        this.tenantId = tenantId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.topic = topic;
        this.payload = payload;
    }

    public void markPublished() {
        this.status = Status.PUBLISHED;
        this.publishedAt = Instant.now();
        this.lastError = null;
    }

    /** Records a delivery failure; the relay decides when attempts are exhausted. */
    public void markAttemptFailed(String error, int maxAttempts) {
        this.attempts++;
        // Error text can carry broker detail; truncate so it always fits the column.
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 512));
        if (this.attempts >= maxAttempts) {
            this.status = Status.FAILED;
        }
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getTopic() {
        return topic;
    }

    public String getPayload() {
        return payload;
    }

    public Status getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public String getLastError() {
        return lastError;
    }
}

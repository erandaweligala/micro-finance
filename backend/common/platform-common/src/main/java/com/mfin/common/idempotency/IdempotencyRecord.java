package com.mfin.common.idempotency;

import com.mfin.common.persistence.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Filter;

import java.time.Instant;

/**
 * Remembers the outcome of a money-moving request so that a retry - a flaky mobile network, a
 * double tap on "Pay", a gateway timeout followed by a client retry - cannot post the payment
 * twice.
 *
 * <p>The unique constraint on {@code (tenant_id, idempotency_key)} is the actual guarantee: it
 * is enforced by the database, so two concurrent replicas racing on the same key end with one
 * insert and one constraint violation, not two payments.</p>
 */
@Entity
@Table(name = "idempotency_record",
        uniqueConstraints = @UniqueConstraint(name = "ux_idempotency_tenant_key",
                columnNames = {"tenant_id", "idempotency_key"}),
        indexes = @Index(name = "ix_idempotency_expires", columnList = "expires_at"))
@Filter(name = TenantAwareEntity.FILTER, condition = TenantAwareEntity.CONDITION)
public class IdempotencyRecord extends TenantAwareEntity {

    @Column(name = "idempotency_key", nullable = false, length = 128, updatable = false)
    private String idempotencyKey;

    /** Endpoint the key was used against; the same key on a different operation is a conflict. */
    @Column(name = "operation", nullable = false, length = 96, updatable = false)
    private String operation;

    /** SHA-256 of the canonical request body, to tell a genuine retry from key reuse. */
    @Column(name = "request_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)", updatable = false)
    private String requestHash;

    /** Serialised original response, replayed verbatim on retry. */
    @Column(name = "response_payload", columnDefinition = "JSON")
    private String responsePayload;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(String idempotencyKey, String operation, String requestHash,
                             Instant expiresAt) {
        this.idempotencyKey = idempotencyKey;
        this.operation = operation;
        this.requestHash = requestHash;
        this.expiresAt = expiresAt;
    }

    public void completeWith(int status, String payload) {
        this.responseStatus = status;
        this.responsePayload = payload;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getOperation() {
        return operation;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getResponsePayload() {
        return responsePayload;
    }

    public int getResponseStatus() {
        return responseStatus;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}

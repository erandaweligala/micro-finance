package com.mfin.reporting.domain;

import com.mfin.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * An immutable audit record.
 *
 * <p>Every column is {@code updatable = false} and the service exposes no update or delete path,
 * but application-level immutability is only a promise. The real control is the <strong>hash
 * chain</strong>: each row stores {@code hash = SHA-256(previousHash || canonical fields)}, so
 * altering or removing any historical row breaks every hash after it. A periodic verification
 * job walks the chain and alerts on the first mismatch, which turns silent tampering - including
 * by someone with database access - into a detectable event.</p>
 */
@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "ix_audit_tenant_time", columnList = "tenant_id, occurred_at"),
        @Index(name = "ix_audit_actor", columnList = "tenant_id, actor_id"),
        @Index(name = "ix_audit_entity", columnList = "tenant_id, entity_type, entity_id"),
        @Index(name = "ix_audit_action", columnList = "tenant_id, action")
})
public class AuditLog extends BaseEntity {

    @Column(name = "tenant_id", columnDefinition = "CHAR(36)", updatable = false)
    private UUID tenantId;

    /** Monotonic position in this tenant's chain; the chain is verified in this order. */
    @Column(name = "sequence_number", nullable = false, updatable = false)
    private long sequenceNumber;

    @Column(name = "action", nullable = false, length = 96, updatable = false)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 64, updatable = false)
    private String entityType;

    @Column(name = "entity_id", columnDefinition = "CHAR(36)", updatable = false)
    private UUID entityId;

    @Column(name = "actor_id", columnDefinition = "CHAR(36)", updatable = false)
    private UUID actorId;

    @Column(name = "actor_name", length = 128, updatable = false)
    private String actorName;

    /** Structured context: what changed, from what to what. Never contains secrets or full PII. */
    @Column(name = "details", columnDefinition = "JSON", updatable = false)
    private String details;

    @Column(name = "source_event_id", columnDefinition = "CHAR(36)", updatable = false)
    private UUID sourceEventId;

    @Column(name = "ip_address", length = 45, updatable = false)
    private String ipAddress;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "previous_hash", length = 64, updatable = false)
    private String previousHash;

    @Column(name = "entry_hash", nullable = false, length = 64, updatable = false)
    private String entryHash;

    protected AuditLog() {
    }

    public AuditLog(UUID tenantId, long sequenceNumber, String action, String entityType,
                    UUID entityId, UUID actorId, String actorName, String details,
                    UUID sourceEventId, Instant occurredAt, String previousHash) {
        this.tenantId = tenantId;
        this.sequenceNumber = sequenceNumber;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.actorId = actorId;
        this.actorName = actorName;
        this.details = details;
        this.sourceEventId = sourceEventId;
        this.occurredAt = occurredAt;
        this.previousHash = previousHash;
        this.entryHash = computeHash();
    }

    /**
     * Recomputes this entry's hash from its own fields and its predecessor's hash.
     * A verification job compares the result with the stored {@link #entryHash}.
     */
    public String computeHash() {
        String canonical = String.join("|",
                previousHash == null ? "" : previousHash,
                tenantId == null ? "" : tenantId.toString(),
                String.valueOf(sequenceNumber),
                action,
                entityType,
                entityId == null ? "" : entityId.toString(),
                actorId == null ? "" : actorId.toString(),
                details == null ? "" : details,
                occurredAt.toString());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    /** True when the stored hash still matches the entry's contents. */
    public boolean isIntact() {
        return entryHash.equals(computeHash());
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getActorName() {
        return actorName;
    }

    public String getDetails() {
        return details;
    }

    public UUID getSourceEventId() {
        return sourceEventId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public String getEntryHash() {
        return entryHash;
    }
}

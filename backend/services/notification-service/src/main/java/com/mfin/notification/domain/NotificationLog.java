package com.mfin.notification.domain;

import com.mfin.common.persistence.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Filter;

import java.time.Instant;
import java.util.UUID;

/**
 * The record of one outbound message.
 *
 * <p>Kept for two reasons that matter in lending: proving a statutory notice was sent, and
 * stopping a redelivered event from messaging the same borrower twice - which the unique
 * constraint on the source event id enforces.</p>
 *
 * <p>The rendered body is stored, but the recipient address is masked in every log line: a
 * notification history is a rich source of personal data if it leaks.</p>
 */
@Entity
@Table(name = "notification_log",
        uniqueConstraints = @UniqueConstraint(name = "ux_notification_source_event",
                columnNames = {"tenant_id", "source_event_id"}),
        indexes = {
                @Index(name = "ix_notification_status", columnList = "tenant_id, status"),
                @Index(name = "ix_notification_recipient", columnList = "tenant_id, related_entity_id")
        })
@Filter(name = TenantAwareEntity.FILTER, condition = TenantAwareEntity.CONDITION)
public class NotificationLog extends TenantAwareEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16)
    private Channel channel;

    @Column(name = "template_code", nullable = false, length = 64)
    private String templateCode;

    /** Masked at write time: "j***@example.com" or "+2547*****88". */
    @Column(name = "recipient_masked", nullable = false, length = 128)
    private String recipientMasked;

    @Column(name = "subject", length = 255)
    private String subject;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 512)
    private String lastError;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "source_event_id", columnDefinition = "CHAR(36)")
    private UUID sourceEventId;

    @Column(name = "related_entity_id", columnDefinition = "CHAR(36)")
    private UUID relatedEntityId;

    public enum Channel {
        EMAIL, SMS, PUSH
    }

    public enum Status {
        PENDING, SENT, FAILED
    }

    protected NotificationLog() {
    }

    public NotificationLog(Channel channel, String templateCode, String recipientMasked,
                           UUID sourceEventId, UUID relatedEntityId) {
        this.channel = channel;
        this.templateCode = templateCode;
        this.recipientMasked = recipientMasked;
        this.sourceEventId = sourceEventId;
        this.relatedEntityId = relatedEntityId;
    }

    public void render(String subject, String body) {
        this.subject = subject;
        this.body = body;
    }

    public void markSent() {
        this.status = Status.SENT;
        this.sentAt = Instant.now();
        this.attempts++;
        this.lastError = null;
    }

    public void markFailed(String error, int maxAttempts) {
        this.attempts++;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 512));
        if (this.attempts >= maxAttempts) {
            this.status = Status.FAILED;
        }
    }

    public Channel getChannel() {
        return channel;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public String getRecipientMasked() {
        return recipientMasked;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public Status getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public UUID getSourceEventId() {
        return sourceEventId;
    }

    public UUID getRelatedEntityId() {
        return relatedEntityId;
    }
}

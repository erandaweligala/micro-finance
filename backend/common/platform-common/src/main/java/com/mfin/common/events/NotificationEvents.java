package com.mfin.common.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Requests for outbound communication, consumed by the notification service. */
public final class NotificationEvents {

    private NotificationEvents() {
    }

    public enum Channel {
        EMAIL, SMS, PUSH
    }

    /**
     * Asks the notification service to send a templated message.
     *
     * <p>Carries template variables rather than rendered text so that the notification service
     * owns wording, localisation and tenant branding in one place. Sensitive values (reset
     * tokens, one-time codes) travel here because the topic is internal, but they are never
     * written to application logs.</p>
     */
    public record NotificationRequested(
            UUID eventId,
            UUID tenantId,
            Channel channel,
            String templateCode,
            String recipient,
            Map<String, String> variables,
            UUID relatedEntityId,
            Instant occurredAt
    ) implements DomainEvent {
        public static final String TYPE = "notification.requested.v1";

        @Override
        public String eventType() {
            return TYPE;
        }

        @Override
        public String topic() {
            return Topics.NOTIFICATION;
        }

        @Override
        public String aggregateType() {
            return "Notification";
        }

        @Override
        public UUID aggregateId() {
            return relatedEntityId != null ? relatedEntityId : eventId;
        }
    }
}

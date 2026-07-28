package com.mfin.common.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Tenant lifecycle events. The identity service consumes these to keep its local sign-in
 * directory current, so authentication never has to call the tenant service synchronously.
 */
public final class TenantEvents {

    private TenantEvents() {
    }

    public record TenantOnboarded(
            UUID eventId,
            UUID tenantId,
            String slug,
            String name,
            String planCode,
            Instant occurredAt
    ) implements DomainEvent {
        public static final String TYPE = "tenant.onboarded.v1";

        @Override
        public String eventType() {
            return TYPE;
        }

        @Override
        public String topic() {
            return Topics.TENANT;
        }

        @Override
        public String aggregateType() {
            return "Tenant";
        }

        @Override
        public UUID aggregateId() {
            return tenantId;
        }
    }

    /** Emitted on suspension or reactivation; gates sign-in for the whole institution. */
    public record TenantStatusChanged(
            UUID eventId,
            UUID tenantId,
            String slug,
            String name,
            String status,
            boolean loginEnabled,
            String reason,
            Instant occurredAt
    ) implements DomainEvent {
        public static final String TYPE = "tenant.status-changed.v1";

        @Override
        public String eventType() {
            return TYPE;
        }

        @Override
        public String topic() {
            return Topics.TENANT;
        }

        @Override
        public String aggregateType() {
            return "Tenant";
        }

        @Override
        public UUID aggregateId() {
            return tenantId;
        }
    }
}

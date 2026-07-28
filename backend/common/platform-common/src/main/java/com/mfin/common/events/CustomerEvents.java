package com.mfin.common.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Customer lifecycle events.
 *
 * <p>Deliberately thin: they carry identifiers and display names, never identity documents or
 * contact details. Events are replicated across topics and consumer databases, so putting PII
 * in them would multiply the number of places that data has to be protected and erased.</p>
 */
public final class CustomerEvents {

    private CustomerEvents() {
    }

    public record CustomerRegistered(
            UUID eventId,
            UUID tenantId,
            UUID customerId,
            String customerNumber,
            String fullName,
            UUID branchId,
            Instant occurredAt
    ) implements DomainEvent {
        public static final String TYPE = "customer.registered.v1";

        @Override
        public String eventType() {
            return TYPE;
        }

        @Override
        public String topic() {
            return Topics.CUSTOMER;
        }

        @Override
        public String aggregateType() {
            return "Customer";
        }

        @Override
        public UUID aggregateId() {
            return customerId;
        }
    }

    public record CustomerUpdated(
            UUID eventId,
            UUID tenantId,
            UUID customerId,
            String fullName,
            Instant occurredAt
    ) implements DomainEvent {
        public static final String TYPE = "customer.updated.v1";

        @Override
        public String eventType() {
            return TYPE;
        }

        @Override
        public String topic() {
            return Topics.CUSTOMER;
        }

        @Override
        public String aggregateType() {
            return "Customer";
        }

        @Override
        public UUID aggregateId() {
            return customerId;
        }
    }

    /** KYC reached a terminal state; origination gates new lending on this. */
    public record KycStatusChanged(
            UUID eventId,
            UUID tenantId,
            UUID customerId,
            String kycStatus,
            UUID decidedBy,
            String reason,
            Instant occurredAt
    ) implements DomainEvent {
        public static final String TYPE = "customer.kyc-status-changed.v1";

        @Override
        public String eventType() {
            return TYPE;
        }

        @Override
        public String topic() {
            return Topics.CUSTOMER;
        }

        @Override
        public String aggregateType() {
            return "Customer";
        }

        @Override
        public UUID aggregateId() {
            return customerId;
        }
    }

    public record CustomerDeactivated(
            UUID eventId,
            UUID tenantId,
            UUID customerId,
            String reason,
            Instant occurredAt
    ) implements DomainEvent {
        public static final String TYPE = "customer.deactivated.v1";

        @Override
        public String eventType() {
            return TYPE;
        }

        @Override
        public String topic() {
            return Topics.CUSTOMER;
        }

        @Override
        public String aggregateType() {
            return "Customer";
        }

        @Override
        public UUID aggregateId() {
            return customerId;
        }
    }
}

package com.mfin.common.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Integration events for money movement. Consumed by loan accounts, the ledger and notifications. */
public final class PaymentEvents {

    private PaymentEvents() {
    }

    /**
     * A repayment was accepted and allocated. Carries the allocation breakdown so the ledger
     * can post double-entry lines without calling back into the payment service.
     */
    public record PaymentPosted(
            UUID eventId,
            UUID tenantId,
            UUID paymentId,
            UUID loanAccountId,
            UUID customerId,
            String receiptNumber,
            BigDecimal amount,
            BigDecimal penaltyAllocated,
            BigDecimal interestAllocated,
            BigDecimal principalAllocated,
            BigDecimal excessCarried,
            BigDecimal outstandingPrincipalAfter,
            BigDecimal totalOutstandingAfter,
            String paymentMethod,
            LocalDate valueDate,
            Instant occurredAt
    ) implements DomainEvent {
        public static final String TYPE = "payment.posted.v1";

        @Override
        public String eventType() {
            return TYPE;
        }

        @Override
        public String topic() {
            return Topics.PAYMENT;
        }

        @Override
        public String aggregateType() {
            return "Payment";
        }

        @Override
        public UUID aggregateId() {
            return paymentId;
        }
    }

    /**
     * A posted payment was reversed under authorisation. The ledger responds with a
     * contra entry rather than deleting the original - financial history is append-only.
     */
    public record PaymentReversed(
            UUID eventId,
            UUID tenantId,
            UUID paymentId,
            UUID loanAccountId,
            UUID reversalOfPaymentId,
            BigDecimal amount,
            String reason,
            UUID authorisedBy,
            Instant occurredAt
    ) implements DomainEvent {
        public static final String TYPE = "payment.reversed.v1";

        @Override
        public String eventType() {
            return TYPE;
        }

        @Override
        public String topic() {
            return Topics.PAYMENT;
        }

        @Override
        public String aggregateType() {
            return "Payment";
        }

        @Override
        public UUID aggregateId() {
            return paymentId;
        }
    }
}

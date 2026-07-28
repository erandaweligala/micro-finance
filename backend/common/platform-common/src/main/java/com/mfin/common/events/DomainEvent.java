package com.mfin.common.events;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.UUID;

/**
 * Contract every integration event implements.
 *
 * <p>Events are the public API between services and are versioned by name
 * ({@code loan.disbursed.v1}); consumers must tolerate unknown fields so producers can add
 * them without a lockstep deploy.</p>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType", include = JsonTypeInfo.As.EXISTING_PROPERTY,
        visible = true)
public interface DomainEvent {

    /** Unique id of this event instance; consumers use it to deduplicate. */
    UUID eventId();

    /** Versioned type name, e.g. {@code loan.disbursed.v1}. */
    String eventType();

    /** Kafka topic this event is published to. */
    String topic();

    /** Aggregate that emitted the event, e.g. {@code LoanAccount}. */
    String aggregateType();

    /** Id of that aggregate; also used as the partition key to preserve per-loan ordering. */
    UUID aggregateId();

    UUID tenantId();

    Instant occurredAt();
}

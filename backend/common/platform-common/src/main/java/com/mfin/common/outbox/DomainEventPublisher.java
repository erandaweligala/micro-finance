package com.mfin.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mfin.common.events.DomainEvent;
import com.mfin.common.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Records domain events for publication.
 *
 * <p>Always call this <em>inside</em> the transaction that performs the state change:
 * {@link Propagation#MANDATORY} makes the requirement a compile-time-ish guarantee by failing
 * fast if no transaction is active, which is what keeps the event and the data atomic.</p>
 */
@Service
public class DomainEventPublisher {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public DomainEventPublisher(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(DomainEvent event) {
        UUID tenantId = TenantContext.current()
                .map(principal -> principal.tenantId())
                .orElse(event.tenantId());
        outboxRepository.save(new OutboxEvent(
                tenantId,
                event.aggregateType(),
                event.aggregateId(),
                event.eventType(),
                event.topic(),
                serialise(event)));
    }

    private String serialise(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "Unable to serialise domain event " + event.eventType(), ex);
        }
    }
}

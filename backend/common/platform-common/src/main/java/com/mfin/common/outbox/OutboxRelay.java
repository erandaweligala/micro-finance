package com.mfin.common.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Drains the outbox to Kafka.
 *
 * <p>Delivery is <em>at least once</em>: an event whose broker acknowledgement is lost will be
 * republished on the next tick, so every consumer in this platform is written to be idempotent
 * (keyed on {@code eventId}). The alternative - marking published before the ack - would be at
 * most once, and silently losing a disbursement event is not acceptable.</p>
 */
@Component
@ConditionalOnProperty(value = "mfin.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties properties;
    private final Counter published;
    private final Counter failed;

    public OutboxRelay(OutboxRepository outboxRepository,
                       KafkaTemplate<String, String> kafkaTemplate,
                       OutboxProperties properties,
                       MeterRegistry meterRegistry) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.published = Counter.builder("mfin.outbox.published")
                .description("Domain events successfully delivered to the broker")
                .register(meterRegistry);
        this.failed = Counter.builder("mfin.outbox.failed")
                .description("Domain events that exhausted their delivery attempts")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${mfin.outbox.poll-interval-ms:2000}")
    @Transactional
    public void drain() {
        List<OutboxEvent> batch = outboxRepository.claimPending(PageRequest.of(0, properties.getBatchSize()));
        if (batch.isEmpty()) {
            return;
        }
        for (OutboxEvent event : batch) {
            deliver(event);
        }
    }

    private void deliver(OutboxEvent event) {
        try {
            Message<String> message = MessageBuilder.withPayload(event.getPayload())
                    .setHeader(KafkaHeaders.TOPIC, event.getTopic())
                    // Partition by aggregate so a loan's events are consumed in order.
                    .setHeader(KafkaHeaders.KEY, event.getAggregateId().toString())
                    .setHeader("eventId", event.getId().toString())
                    .setHeader("eventType", event.getEventType())
                    .setHeader("tenantId", event.getTenantId() == null ? "" : event.getTenantId().toString())
                    .build();
            kafkaTemplate.send(message).get(properties.getSendTimeoutMs(), TimeUnit.MILLISECONDS);
            event.markPublished();
            published.increment();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            event.markAttemptFailed("Interrupted while publishing", properties.getMaxAttempts());
        } catch (Exception ex) {
            event.markAttemptFailed(ex.getMessage(), properties.getMaxAttempts());
            if (event.getStatus() == OutboxEvent.Status.FAILED) {
                failed.increment();
                // Alert-worthy: a business fact exists with no corresponding event.
                log.error("Outbox event {} ({}) permanently failed after {} attempts",
                        event.getId(), event.getEventType(), event.getAttempts(), ex);
            } else {
                log.warn("Outbox event {} delivery attempt {} failed: {}",
                        event.getId(), event.getAttempts(), ex.getMessage());
            }
        }
    }

    /** Keeps the outbox small; delivered events are retained briefly for troubleshooting. */
    @Scheduled(cron = "${mfin.outbox.purge-cron:0 30 2 * * *}")
    @Transactional
    public void purgeDelivered() {
        Instant cutoff = Instant.now().minus(properties.getRetentionDays(), ChronoUnit.DAYS);
        int removed = outboxRepository.deletePublishedBefore(cutoff);
        if (removed > 0) {
            log.info("Purged {} delivered outbox events older than {}", removed, cutoff);
        }
    }
}

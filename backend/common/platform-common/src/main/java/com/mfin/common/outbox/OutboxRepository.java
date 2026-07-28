package com.mfin.common.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Claims a batch of undelivered events.
     *
     * <p>{@code PESSIMISTIC_WRITE} lets several replicas drain the same outbox concurrently
     * without any of them publishing the same event twice. Ordering by creation time preserves
     * per-aggregate causality closely enough for consumers, which deduplicate regardless.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from OutboxEvent e where e.status = :status order by e.createdAt asc")
    List<OutboxEvent> claimByStatus(@Param("status") OutboxEvent.Status status, Pageable pageable);

    default List<OutboxEvent> claimPending(Pageable pageable) {
        return claimByStatus(OutboxEvent.Status.PENDING, pageable);
    }

    long countByStatus(OutboxEvent.Status status);

    @Modifying
    @Query("delete from OutboxEvent e where e.status = :status and e.publishedAt < :before")
    int deleteByStatusAndPublishedAtBefore(@Param("status") OutboxEvent.Status status,
                                           @Param("before") Instant before);

    default int deletePublishedBefore(Instant before) {
        return deleteByStatusAndPublishedAtBefore(OutboxEvent.Status.PUBLISHED, before);
    }
}

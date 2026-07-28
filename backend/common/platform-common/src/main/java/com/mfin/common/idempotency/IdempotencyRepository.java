package com.mfin.common.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, UUID> {

    /** Tenant id is an explicit predicate, not left to the Hibernate filter alone. */
    Optional<IdempotencyRecord> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    @Modifying
    @Query("delete from IdempotencyRecord r where r.expiresAt < :now")
    int deleteByExpiresAtBefore(@Param("now") Instant now);
}

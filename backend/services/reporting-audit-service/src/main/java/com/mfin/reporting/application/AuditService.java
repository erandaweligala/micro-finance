package com.mfin.reporting.application;

import com.mfin.common.tenant.TenantContext;
import com.mfin.common.web.PageResponse;
import com.mfin.reporting.domain.AuditLog;
import com.mfin.reporting.repository.ReportingRepositories.AuditLogRepository;
import com.mfin.reporting.web.ReportingDtos.AuditEntryResponse;
import com.mfin.reporting.web.ReportingDtos.ChainVerificationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Writes and verifies the immutable audit trail.
 *
 * <p>Entries are only ever appended, and each links to the previous one by hash. Verification
 * walks the chain and reports the first break, which is what makes after-the-fact tampering
 * detectable rather than merely discouraged.</p>
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /** Appends an entry. Idempotent on the source event so a redelivery does not duplicate history. */
    @Transactional
    public void record(UUID tenantId, String action, String entityType, UUID entityId,
                       UUID actorId, String actorName, String details, UUID sourceEventId,
                       Instant occurredAt) {
        if (sourceEventId != null && repository.existsByTenantIdAndSourceEventId(tenantId, sourceEventId)) {
            return;
        }
        String previousHash = repository.findFirstByTenantIdOrderBySequenceNumberDesc(tenantId)
                .map(AuditLog::getEntryHash)
                .orElse(null);
        long sequence = repository.maxSequence(tenantId) + 1;
        repository.save(new AuditLog(tenantId, sequence, action, entityType, entityId, actorId,
                actorName, details, sourceEventId, occurredAt, previousHash));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEntryResponse> search(String action, String entityType, UUID entityId,
                                                   UUID actorId, Instant from, Instant to,
                                                   Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();
        return PageResponse.from(repository.search(tenantId, action, entityType, entityId, actorId,
                from, to, pageable), AuditEntryResponse::from);
    }

    /**
     * Verifies the whole chain for the caller's institution.
     *
     * <p>Reports the first sequence number where either an entry's own hash no longer matches
     * its contents, or its link to the previous entry is broken - the two signatures of an
     * edited or deleted row.</p>
     */
    @Transactional(readOnly = true)
    public ChainVerificationResult verifyChain() {
        UUID tenantId = TenantContext.requireTenantId();
        List<AuditLog> entries = repository.findByTenantIdOrderBySequenceNumberAsc(tenantId);

        String expectedPrevious = null;
        for (AuditLog entry : entries) {
            boolean linkIntact = expectedPrevious == null
                    ? entry.getPreviousHash() == null
                    : expectedPrevious.equals(entry.getPreviousHash());
            if (!linkIntact || !entry.isIntact()) {
                log.error("Audit chain broken for tenant {} at sequence {}", tenantId,
                        entry.getSequenceNumber());
                return new ChainVerificationResult(false, entries.size(), entry.getSequenceNumber(),
                        "The audit chain is broken at this entry; history may have been altered");
            }
            expectedPrevious = entry.getEntryHash();
        }
        return new ChainVerificationResult(true, entries.size(), null, "The audit chain is intact");
    }
}

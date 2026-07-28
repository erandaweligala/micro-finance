package com.mfin.common.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mfin.common.error.ApiExceptions;
import com.mfin.common.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Executes an operation at most once per idempotency key.
 *
 * <p>Semantics, which match Stripe's and are what the mobile client expects:</p>
 * <ul>
 *   <li>First call with a key: the operation runs and its response is recorded.</li>
 *   <li>Retry with the same key <em>and the same body</em>: the recorded response is replayed,
 *       and the operation does not run again.</li>
 *   <li>Same key with a <em>different</em> body: 409, because the client has a bug and silently
 *       accepting either outcome would be worse than failing loudly.</li>
 * </ul>
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final int RETENTION_HOURS = 24;

    private final IdempotencyRepository repository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * @param key       client-supplied {@code Idempotency-Key} header
     * @param operation logical operation name, e.g. {@code payment.capture}
     * @param request   the request payload, hashed to detect key reuse
     * @param work      the operation to perform exactly once
     * @param type      response type, for replaying the recorded response
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public <T> T execute(String key, String operation, Object request, Class<T> type,
                         Supplier<T> work) {
        UUID tenantId = TenantContext.requireTenantId();
        String requestHash = hash(request);

        Optional<IdempotencyRecord> existing =
                repository.findByTenantIdAndIdempotencyKey(tenantId, key);
        if (existing.isPresent()) {
            return replay(existing.get(), key, operation, requestHash, type);
        }

        IdempotencyRecord record = new IdempotencyRecord(key, operation, requestHash,
                Instant.now().plus(RETENTION_HOURS, ChronoUnit.HOURS));
        try {
            // Claim the key first: the unique index is what serialises concurrent retries.
            repository.saveAndFlush(record);
        } catch (DataIntegrityViolationException ex) {
            // Another request won the race; fall back to replaying whatever it recorded.
            IdempotencyRecord winner = repository.findByTenantIdAndIdempotencyKey(tenantId, key)
                    .orElseThrow(() -> new ApiExceptions.ConflictException(
                            "Concurrent request with the same idempotency key is still in flight"));
            return replay(winner, key, operation, requestHash, type);
        }

        T response = work.get();
        record.completeWith(200, serialise(response));
        repository.save(record);
        return response;
    }

    private <T> T replay(IdempotencyRecord record, String key, String operation,
                         String requestHash, Class<T> type) {
        if (!record.getOperation().equals(operation) || !record.getRequestHash().equals(requestHash)) {
            throw new ApiExceptions.IdempotencyConflictException(key);
        }
        if (record.getResponsePayload() == null) {
            // The original attempt claimed the key but has not committed its response yet.
            throw new ApiExceptions.ConflictException(
                    "A request with this idempotency key is still being processed");
        }
        log.info("Replaying idempotent response for operation {} (key ending {})",
                operation, key.length() > 4 ? key.substring(key.length() - 4) : "****");
        return deserialise(record.getResponsePayload(), type);
    }

    /** Canonical hash of the request, so semantically identical retries match. */
    private String hash(Object request) {
        try {
            byte[] canonical = objectMapper.writeValueAsBytes(request);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical));
        } catch (NoSuchAlgorithmException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Unable to hash idempotent request", ex);
        }
    }

    private String serialise(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Unable to record idempotent response", ex);
        }
    }

    private <T> T deserialise(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Unable to replay idempotent response", ex);
        }
    }

    /** Keys are only useful for as long as a client might retry. */
    @Transactional
    public int purgeExpired() {
        return repository.deleteByExpiresAtBefore(Instant.now());
    }
}

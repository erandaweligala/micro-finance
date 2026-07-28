package com.mfin.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * The single error shape every service returns. Machine-readable {@code code} for clients to
 * branch on, human-readable {@code message} for display, {@code traceId} to join the request
 * to its logs and spans.
 *
 * <p>It deliberately carries no stack traces, SQL, or internal identifiers - error responses
 * are an information-disclosure surface.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String traceId,
        List<FieldViolation> errors
) {

    /** One rejected field, as produced by Bean Validation. */
    public record FieldViolation(String field, String message, Object rejectedValue) {
    }

    public static ApiError of(int status, String code, String message, String path, String traceId) {
        return new ApiError(Instant.now(), status, code, message, path, traceId, null);
    }

    public ApiError withErrors(List<FieldViolation> violations) {
        return new ApiError(timestamp, status, code, message, path, traceId,
                violations == null || violations.isEmpty() ? null : List.copyOf(violations));
    }
}

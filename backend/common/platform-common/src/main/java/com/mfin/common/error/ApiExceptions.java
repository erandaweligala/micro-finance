package com.mfin.common.error;

import org.springframework.http.HttpStatus;

/**
 * The exception vocabulary shared by every service. Each carries an {@link ErrorCodes} value
 * and the HTTP status it maps to, so {@code GlobalExceptionHandler} needs no per-service rules.
 */
public final class ApiExceptions {

    private ApiExceptions() {
    }

    /** Root of the hierarchy; anything thrown deliberately by business code extends this. */
    public abstract static class ApiException extends RuntimeException {
        private final String code;
        private final HttpStatus status;

        protected ApiException(HttpStatus status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }

        public String getCode() {
            return code;
        }

        public HttpStatus getStatus() {
            return status;
        }
    }

    /** 404 - the resource does not exist, or does not exist <em>for this tenant</em>. */
    public static class ResourceNotFoundException extends ApiException {
        public ResourceNotFoundException(String resource, Object id) {
            super(HttpStatus.NOT_FOUND, ErrorCodes.RESOURCE_NOT_FOUND, resource + " " + id + " was not found");
        }

        public ResourceNotFoundException(String message) {
            super(HttpStatus.NOT_FOUND, ErrorCodes.RESOURCE_NOT_FOUND, message);
        }
    }

    /** 422 - syntactically valid, but not allowed by a domain rule. */
    public static class BusinessRuleException extends ApiException {
        public BusinessRuleException(String message) {
            super(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCodes.BUSINESS_RULE_VIOLATION, message);
        }

        public BusinessRuleException(String code, String message) {
            super(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
        }
    }

    /** 422 - the requested transition is not legal from the entity's current state. */
    public static class IllegalStateTransitionException extends ApiException {
        public IllegalStateTransitionException(String entity, Object from, Object to) {
            super(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCodes.ILLEGAL_STATE_TRANSITION,
                    entity + " cannot move from " + from + " to " + to);
        }
    }

    /** 409 - the request conflicts with the current state (duplicate key, stale version). */
    public static class ConflictException extends ApiException {
        public ConflictException(String message) {
            super(HttpStatus.CONFLICT, ErrorCodes.CONFLICT, message);
        }

        public ConflictException(String code, String message) {
            super(HttpStatus.CONFLICT, code, message);
        }
    }

    /**
     * 409 - an idempotency key was replayed with a <em>different</em> payload. Replaying the
     * same payload is not an error: the original response is returned instead.
     */
    public static class IdempotencyConflictException extends ApiException {
        public IdempotencyConflictException(String key) {
            super(HttpStatus.CONFLICT, ErrorCodes.IDEMPOTENCY_KEY_REUSED,
                    "Idempotency key '" + key + "' was already used for a different request");
        }
    }

    /** 403 - authenticated, but not entitled to this tenant's data or this operation. */
    public static class AccessDeniedException extends ApiException {
        public AccessDeniedException(String message) {
            super(HttpStatus.FORBIDDEN, ErrorCodes.ACCESS_DENIED, message);
        }
    }

    /** 402/403 - the tenant's subscription plan does not include this feature or capacity. */
    public static class SubscriptionLimitException extends ApiException {
        public SubscriptionLimitException(String code, String message) {
            super(HttpStatus.FORBIDDEN, code, message);
        }
    }

    /** 503 - a downstream service is unavailable; Resilience4j has already given up retrying. */
    public static class UpstreamUnavailableException extends ApiException {
        public UpstreamUnavailableException(String service) {
            super(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.UPSTREAM_UNAVAILABLE,
                    service + " is temporarily unavailable, please retry");
        }
    }
}

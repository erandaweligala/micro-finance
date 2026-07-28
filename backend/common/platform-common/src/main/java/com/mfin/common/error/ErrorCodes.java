package com.mfin.common.error;

/** Stable machine-readable error codes. Clients branch on these, so they must not be renamed. */
public final class ErrorCodes {

    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String MALFORMED_REQUEST = "MALFORMED_REQUEST";
    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String BUSINESS_RULE_VIOLATION = "BUSINESS_RULE_VIOLATION";
    public static final String CONFLICT = "CONFLICT";
    public static final String DUPLICATE_REQUEST = "DUPLICATE_REQUEST";
    public static final String IDEMPOTENCY_KEY_REUSED = "IDEMPOTENCY_KEY_REUSED";
    public static final String CONCURRENT_MODIFICATION = "CONCURRENT_MODIFICATION";
    public static final String UNAUTHENTICATED = "UNAUTHENTICATED";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String TENANT_RESOLUTION_FAILED = "TENANT_RESOLUTION_FAILED";
    public static final String TENANT_LIMIT_EXCEEDED = "TENANT_LIMIT_EXCEEDED";
    public static final String FEATURE_NOT_ENABLED = "FEATURE_NOT_ENABLED";
    public static final String LOAN_CALCULATION_INVALID = "LOAN_CALCULATION_INVALID";
    public static final String ILLEGAL_STATE_TRANSITION = "ILLEGAL_STATE_TRANSITION";
    public static final String UPSTREAM_UNAVAILABLE = "UPSTREAM_UNAVAILABLE";
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private ErrorCodes() {
    }
}

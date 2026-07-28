package com.mfin.tenant.domain;

public enum TenantStatus {

    /** Created but not yet live; no one can sign in. */
    PENDING_ACTIVATION,

    ACTIVE,

    /** Barred from signing in, usually for non-payment. Data is retained. */
    SUSPENDED,

    /** Contract ended. Retained for the statutory period, then exported and purged. */
    TERMINATED
}

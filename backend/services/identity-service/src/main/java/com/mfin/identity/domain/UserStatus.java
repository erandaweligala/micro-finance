package com.mfin.identity.domain;

public enum UserStatus {

    /** Can sign in. */
    ACTIVE,

    /** Created but not yet verified; cannot sign in. */
    PENDING_ACTIVATION,

    /** Temporarily barred by an administrator. */
    SUSPENDED,

    /**
     * Permanently disabled. The row is retained rather than deleted so that historical
     * audit entries and loan approvals keep pointing at a real user.
     */
    DEACTIVATED
}

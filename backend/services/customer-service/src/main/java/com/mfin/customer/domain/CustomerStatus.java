package com.mfin.customer.domain;

/**
 * Customers are never deleted: loans, ledgers and audit trails must keep resolving to a real
 * person for the statutory retention period.
 */
public enum CustomerStatus {

    ACTIVE,

    /** Temporarily barred from new borrowing, e.g. pending a fraud review. */
    SUSPENDED,

    /** Closed relationship. Existing loans continue to be serviced. */
    DEACTIVATED
}

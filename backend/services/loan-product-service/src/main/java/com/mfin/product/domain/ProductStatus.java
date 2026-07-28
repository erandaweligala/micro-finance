package com.mfin.product.domain;

/** Lifecycle of a loan product. */
public enum ProductStatus {

    /** Being configured; cannot be used for applications. */
    DRAFT,

    /** Available for new loan applications. */
    ACTIVE,

    /** Withdrawn from sale. Existing loans continue to run to maturity. */
    INACTIVE
}

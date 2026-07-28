package com.mfin.customer.domain;

/** Know-your-customer verification state. Lending requires VERIFIED. */
public enum KycStatus {

    /** Registered, documents not yet submitted or not yet complete. */
    PENDING,

    /** Documents submitted and awaiting an officer's decision. */
    UNDER_REVIEW,

    VERIFIED,

    /** Rejected with a reason; the customer may resubmit. */
    REJECTED,

    /** Previously verified but a document has since expired. */
    EXPIRED
}

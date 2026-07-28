package com.mfin.common.events;

/**
 * Kafka topic names. Keyed by loan or customer id so that all events for one aggregate land on
 * the same partition and are therefore consumed in order.
 */
public final class Topics {

    public static final String CUSTOMER = "mfin.customer";
    public static final String LOAN_APPLICATION = "mfin.loan-application";
    public static final String LOAN_ACCOUNT = "mfin.loan-account";
    public static final String PAYMENT = "mfin.payment";
    public static final String LEDGER = "mfin.ledger";
    public static final String NOTIFICATION = "mfin.notification";
    public static final String AUDIT = "mfin.audit";
    public static final String TENANT = "mfin.tenant";

    /** Dead-letter suffix applied by consumers that exhaust their retries. */
    public static final String DLT_SUFFIX = ".DLT";

    private Topics() {
    }
}

package com.mfin.loan.engine;

/** When and how the processing fee is collected from the borrower. */
public enum FeeCollection {

    /** Netted off the disbursed amount; the borrower still repays the full principal. */
    DEDUCT_FROM_DISBURSEMENT,

    /** Added in full to the first installment. */
    ADD_TO_FIRST_INSTALLMENT,

    /** Spread evenly across every installment (residual rounding lands on the last one). */
    SPREAD_ACROSS_INSTALLMENTS
}

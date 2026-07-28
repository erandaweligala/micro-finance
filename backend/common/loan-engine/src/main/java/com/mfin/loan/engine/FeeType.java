package com.mfin.loan.engine;

/** How a processing fee is quoted. */
public enum FeeType {

    /** A fixed amount in the loan currency. */
    FLAT_AMOUNT,

    /** A percentage of the approved principal. */
    PERCENT_OF_PRINCIPAL
}

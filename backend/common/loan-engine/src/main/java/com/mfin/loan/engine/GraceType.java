package com.mfin.loan.engine;

/** Behaviour of the grace (moratorium) period at the start of a loan. */
public enum GraceType {

    /** No grace period. */
    NONE,

    /**
     * Interest-only period: the borrower pays the accrued interest but no principal.
     * Principal amortisation is compressed into the remaining installments.
     */
    PRINCIPAL_GRACE,

    /**
     * Full moratorium: nothing is due during the grace period and the accrued interest is
     * capitalised into the outstanding principal.
     */
    FULL_GRACE
}

package com.mfin.product.domain;

/** The amount a late-payment penalty is charged on. */
public enum PenaltyBasis {

    /** Penalty accrues on the overdue principal only. */
    OVERDUE_PRINCIPAL,

    /** Penalty accrues on the whole overdue amount, principal and interest together. */
    OVERDUE_TOTAL,

    /** A flat percentage of the missed installment, charged once per missed installment. */
    INSTALLMENT_AMOUNT
}

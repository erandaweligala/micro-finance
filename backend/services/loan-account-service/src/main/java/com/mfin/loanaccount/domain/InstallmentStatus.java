package com.mfin.loanaccount.domain;

/** Settlement state of a single installment. */
public enum InstallmentStatus {

    /** Not yet due, nothing paid. */
    PENDING,

    /** Something has been received, but not the whole installment. */
    PARTIALLY_PAID,

    PAID,

    /** Past its due date with a balance outstanding. */
    OVERDUE,

    /** Written off or forgiven by an authorised user. */
    WAIVED
}

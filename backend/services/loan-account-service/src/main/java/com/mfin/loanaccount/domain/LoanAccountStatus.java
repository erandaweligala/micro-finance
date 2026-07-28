package com.mfin.loanaccount.domain;

/** Lifecycle of a disbursed loan. */
public enum LoanAccountStatus {

    /** Performing: disbursed and not in arrears. */
    ACTIVE,

    /** At least one installment is past its due date. */
    OVERDUE,

    /** Past the product's days-to-default threshold. Still collectable. */
    DEFAULTED,

    /** Fully repaid. */
    CLOSED,

    /** Removed from the performing book; a recovery may still be posted against it. */
    WRITTEN_OFF
}

package com.mfin.ledger.domain;

/** What a ledger line represents. Debits increase the debt, credits reduce it. */
public enum LedgerTransactionType {

    /** Loan principal released to the borrower. Debit. */
    DISBURSEMENT,

    /** Contractual interest charged. Debit. */
    INTEREST_CHARGE,

    /** Processing or service fee charged. Debit. */
    FEE_CHARGE,

    /** Late-payment penalty accrued. Debit. */
    PENALTY_CHARGE,

    /** Money received from the borrower. Credit. */
    REPAYMENT,

    /** Contra entry undoing a repayment. Debit. */
    REPAYMENT_REVERSAL,

    /** Interest, fee or penalty forgiven by an authorised user. Credit. */
    WAIVER,

    /** Balance removed from the performing book. Credit. */
    WRITE_OFF,

    /** Recovery received after a write-off. Credit. */
    RECOVERY
}

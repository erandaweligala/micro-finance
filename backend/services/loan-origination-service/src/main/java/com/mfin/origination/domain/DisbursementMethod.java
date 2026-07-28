package com.mfin.origination.domain;

/** How funds were released to the borrower. */
public enum DisbursementMethod {
    CASH,
    BANK_TRANSFER,
    MOBILE_MONEY,
    CHEQUE,
    /** Credited against another loan being refinanced. */
    INTERNAL_TRANSFER
}

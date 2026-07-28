package com.mfin.payment.domain;

/** Channel a repayment arrived through. Drives reconciliation and cash-drawer reporting. */
public enum PaymentMethod {
    CASH,
    MOBILE_MONEY,
    BANK_TRANSFER,
    CHEQUE,
    DEBIT_ORDER,
    /** Applied from a credit balance or another account. */
    INTERNAL_TRANSFER
}

package com.mfin.payment.domain;

/**
 * A payment is only ever POSTED or REVERSED. There is no "deleted": corrections are made by
 * reversal so the original entry stays visible on the customer's ledger.
 */
public enum PaymentStatus {
    POSTED,
    REVERSED
}

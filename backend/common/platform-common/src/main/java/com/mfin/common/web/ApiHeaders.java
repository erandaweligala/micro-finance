package com.mfin.common.web;

/** Custom headers understood across the platform. */
public final class ApiHeaders {

    /**
     * Client-generated key that makes a money-moving POST safe to retry. Required on payment
     * capture and reversal; see {@code IdempotencyService}.
     */
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    public static final String REQUEST_ID = RequestIdFilter.HEADER;

    private ApiHeaders() {
    }
}

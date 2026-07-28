package com.mfin.product.domain;

/**
 * How the institution advertises the rate. Storage is always a nominal annual percentage;
 * this only controls presentation and the figure a loan officer types in.
 */
public enum RateQuotation {

    /** Quoted per year, e.g. "18% p.a.". */
    ANNUAL,

    /** Quoted per month, e.g. "1.5% per month" - twelve times smaller than the stored value. */
    MONTHLY
}

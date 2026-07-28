package com.mfin.loan.engine;

/** How interest is derived from the principal. */
public enum InterestMethod {

    /**
     * Interest accrues on the declining outstanding principal. The installment is the
     * annuity payment: {@code P*i*(1+i)^n / ((1+i)^n - 1)}.
     */
    REDUCING_BALANCE,

    /**
     * Interest is charged on the original principal for the whole tenor:
     * {@code P * annualRate * years}, spread evenly across installments.
     */
    FLAT
}

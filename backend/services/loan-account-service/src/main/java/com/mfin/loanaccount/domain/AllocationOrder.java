package com.mfin.loanaccount.domain;

import java.util.List;

/**
 * The order in which a repayment is applied across the buckets of an installment.
 *
 * <p>Configurable because it is a policy choice with real consequences: penalties-first
 * maximises fee recovery, principal-first is cheaper for the borrower, and several
 * jurisdictions mandate one or the other for consumer credit.</p>
 */
public enum AllocationOrder {

    /** Regulatory default in most markets: costs first, then the debt itself. */
    PENALTY_FEE_INTEREST_PRINCIPAL(List.of(Bucket.PENALTY, Bucket.FEE, Bucket.INTEREST, Bucket.PRINCIPAL)),

    /** Borrower-friendly: reduces the balance that interest is charged on soonest. */
    PRINCIPAL_INTEREST_FEE_PENALTY(List.of(Bucket.PRINCIPAL, Bucket.INTEREST, Bucket.FEE, Bucket.PENALTY)),

    /** Interest before charges, used where penalties may not outrank contractual interest. */
    INTEREST_PRINCIPAL_FEE_PENALTY(List.of(Bucket.INTEREST, Bucket.PRINCIPAL, Bucket.FEE, Bucket.PENALTY));

    /** The four things a repayment can settle. */
    public enum Bucket {
        PENALTY, FEE, INTEREST, PRINCIPAL
    }

    private final List<Bucket> order;

    AllocationOrder(List<Bucket> order) {
        this.order = order;
    }

    public List<Bucket> buckets() {
        return order;
    }
}

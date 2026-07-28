package com.mfin.origination.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle of a loan application.
 *
 * <p>The permitted transitions are declared here rather than scattered through service methods,
 * so there is one place to read - and one place to test - what may follow what. Anything not
 * listed is rejected with a 422.</p>
 */
public enum LoanApplicationStatus {

    /** Being captured; freely editable and not yet visible to approvers. */
    DRAFT,

    /** Submitted for assessment. */
    SUBMITTED,

    /** An officer has picked it up and is assessing it. */
    UNDER_REVIEW,

    /** All required approval levels have signed off; awaiting disbursement. */
    APPROVED,

    REJECTED,

    /** Withdrawn by the applicant or the institution before a decision. */
    CANCELLED,

    /** Funds released; the loan account service takes ownership from here. */
    DISBURSED;

    private static final Map<LoanApplicationStatus, Set<LoanApplicationStatus>> TRANSITIONS = Map.of(
            DRAFT, EnumSet.of(SUBMITTED, CANCELLED),
            SUBMITTED, EnumSet.of(UNDER_REVIEW, APPROVED, REJECTED, CANCELLED),
            UNDER_REVIEW, EnumSet.of(APPROVED, REJECTED, CANCELLED),
            // An approved loan can still be rejected or cancelled right up until the money moves.
            APPROVED, EnumSet.of(DISBURSED, REJECTED, CANCELLED),
            REJECTED, EnumSet.noneOf(LoanApplicationStatus.class),
            CANCELLED, EnumSet.noneOf(LoanApplicationStatus.class),
            DISBURSED, EnumSet.noneOf(LoanApplicationStatus.class));

    public boolean canTransitionTo(LoanApplicationStatus target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    /** Terminal states are never edited again; they exist for the record. */
    public boolean isTerminal() {
        return TRANSITIONS.getOrDefault(this, Set.of()).isEmpty();
    }

    public boolean isEditable() {
        return this == DRAFT;
    }
}

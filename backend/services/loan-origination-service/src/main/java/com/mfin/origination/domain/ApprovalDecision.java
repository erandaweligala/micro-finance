package com.mfin.origination.domain;

/** The kind of entry recorded in an application's approval history. */
public enum ApprovalDecision {

    /** An officer took the application up for assessment. */
    REVIEWED,

    APPROVED,

    REJECTED,

    /** Withdrawn before a decision was reached. */
    CANCELLED
}

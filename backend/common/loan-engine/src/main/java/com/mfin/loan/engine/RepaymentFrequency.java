package com.mfin.loan.engine;

import java.time.LocalDate;

/**
 * Repayment cadence. {@link #periodsPerYear()} is the divisor used to convert a nominal
 * annual rate into a periodic rate; {@link #next(LocalDate, int)} walks the due-date calendar.
 */
public enum RepaymentFrequency {

    WEEKLY(52),
    BIWEEKLY(26),
    MONTHLY(12),
    QUARTERLY(4),
    SEMI_ANNUAL(2),
    ANNUAL(1);

    private final int periodsPerYear;

    RepaymentFrequency(int periodsPerYear) {
        this.periodsPerYear = periodsPerYear;
    }

    public int periodsPerYear() {
        return periodsPerYear;
    }

    /**
     * Advances {@code from} by {@code periods} repayment periods.
     * Month-based cadences clamp the day-of-month (31 Jan + 1 month = 28/29 Feb).
     */
    public LocalDate next(LocalDate from, int periods) {
        return switch (this) {
            case WEEKLY -> from.plusWeeks(periods);
            case BIWEEKLY -> from.plusWeeks(2L * periods);
            case MONTHLY -> from.plusMonths(periods);
            case QUARTERLY -> from.plusMonths(3L * periods);
            case SEMI_ANNUAL -> from.plusMonths(6L * periods);
            case ANNUAL -> from.plusYears(periods);
        };
    }

    public LocalDate next(LocalDate from) {
        return next(from, 1);
    }
}

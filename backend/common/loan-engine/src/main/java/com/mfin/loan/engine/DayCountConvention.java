package com.mfin.loan.engine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Day-count basis used for broken (partial) periods - the stub between disbursement and
 * the first scheduled due date when that gap is not exactly one repayment period.
 */
public enum DayCountConvention {

    ACTUAL_365(365),
    ACTUAL_360(360),
    THIRTY_360(360);

    private final int daysInYear;

    DayCountConvention(int daysInYear) {
        this.daysInYear = daysInYear;
    }

    public int daysInYear() {
        return daysInYear;
    }

    /** Day count between two dates under this convention. May be negative if {@code to < from}. */
    public long days(LocalDate from, LocalDate to) {
        if (this == THIRTY_360) {
            int d1 = Math.min(from.getDayOfMonth(), 30);
            int d2 = Math.min(to.getDayOfMonth(), 30);
            return 360L * (to.getYear() - from.getYear())
                    + 30L * (to.getMonthValue() - from.getMonthValue())
                    + (d2 - d1);
        }
        return ChronoUnit.DAYS.between(from, to);
    }

    /** Year fraction between two dates, at full precision. */
    public BigDecimal yearFraction(LocalDate from, LocalDate to) {
        return BigDecimal.valueOf(days(from, to))
                .divide(BigDecimal.valueOf(daysInYear), Money.MC);
    }
}

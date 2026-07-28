package com.mfin.loan.engine;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of the repayment schedule.
 *
 * <p>Invariant: {@code totalDue == principal + interest + fee} and
 * {@code closingBalance == openingBalance - principal} (both to the currency scale).</p>
 *
 * @param installmentNumber   1-based sequence number
 * @param dueDate             date the installment falls due
 * @param openingBalance      principal outstanding before this installment
 * @param principal           principal repaid in this installment
 * @param interest            interest charged for this period
 * @param fee                 fee portion allocated to this installment
 * @param totalDue            total amount payable on {@code dueDate}
 * @param closingBalance      principal outstanding after this installment
 * @param cumulativePrincipal principal repaid to date, inclusive
 * @param cumulativeInterest  interest paid to date, inclusive
 * @param graceInstallment    true when this row falls inside the moratorium
 */
public record ScheduledInstallment(
        int installmentNumber,
        LocalDate dueDate,
        BigDecimal openingBalance,
        BigDecimal principal,
        BigDecimal interest,
        BigDecimal fee,
        BigDecimal totalDue,
        BigDecimal closingBalance,
        BigDecimal cumulativePrincipal,
        BigDecimal cumulativeInterest,
        boolean graceInstallment
) {
}

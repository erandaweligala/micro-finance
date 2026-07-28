package com.mfin.loan.engine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * The calculated amortisation result: headline figures plus the full installment table.
 *
 * @param principal          approved principal
 * @param totalInterest      sum of all interest across the schedule
 * @param totalFees          processing fees charged to the borrower
 * @param totalRepayable     principal + interest + fees payable over the life of the loan
 * @param regularInstallment the level installment (the final row may differ by a rounding residual)
 * @param netDisbursedAmount cash actually released, after any fee deducted at disbursement
 * @param periodicRate       periodic interest rate as a fraction, for audit and disclosure
 * @param effectiveFirstDueDate first due date used
 * @param maturityDate       due date of the final installment
 * @param brokenPeriodInterest stub interest folded into the first installment, if any
 * @param installments       the schedule rows, ordered by installment number
 */
public record LoanSchedule(
        InterestMethod interestMethod,
        RepaymentFrequency frequency,
        BigDecimal principal,
        BigDecimal totalInterest,
        BigDecimal totalFees,
        BigDecimal totalRepayable,
        BigDecimal regularInstallment,
        BigDecimal netDisbursedAmount,
        BigDecimal periodicRate,
        LocalDate effectiveFirstDueDate,
        LocalDate maturityDate,
        BigDecimal brokenPeriodInterest,
        List<ScheduledInstallment> installments
) {

    public LoanSchedule {
        installments = installments == null ? List.of() : Collections.unmodifiableList(installments);
    }

    public int numberOfInstallments() {
        return installments.size();
    }
}

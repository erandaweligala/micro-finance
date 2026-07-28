package com.mfin.loan.engine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Amortisation engine. Stateless and thread-safe - a single instance may be shared.
 *
 * <h2>Reducing balance</h2>
 * <pre>
 *   i = annualRate / periodsPerYear / 100
 *   installment = P * i * (1+i)^n / ((1+i)^n - 1)        (i &gt; 0)
 *   installment = P / n                                   (i = 0)
 * </pre>
 * Interest for each period is charged on the outstanding principal at the start of that
 * period; the principal component is the remainder of the level installment.
 *
 * <h2>Flat rate</h2>
 * <pre>
 *   totalInterest = P * annualRate/100 * (n / periodsPerYear)
 *   installment   = (P + totalInterest) / n
 * </pre>
 *
 * <h2>Rounding</h2>
 * Every published amount is rounded to the loan currency scale. Rounding residue is
 * carried by the <em>final</em> installment, so the schedule always closes at exactly zero
 * outstanding principal and the component sums equal the headline totals to the cent.
 */
public class LoanCalculator {

    /**
     * Builds the full repayment schedule for the supplied terms.
     *
     * @throws LoanCalculationException when the terms are not amortisable
     */
    public LoanSchedule generate(LoanCalculationRequest request) {
        if (request == null) {
            throw new LoanCalculationException("request", "Calculation request is required");
        }
        return switch (request.interestMethod()) {
            case REDUCING_BALANCE -> reducingBalance(request);
            case FLAT -> flat(request);
        };
    }

    // ------------------------------------------------------------------ reducing balance

    private LoanSchedule reducingBalance(LoanCalculationRequest request) {
        int scale = request.currencyScale();
        int total = request.numberOfInstallments();
        int grace = request.gracePeriods();
        BigDecimal rate = request.periodicRate();
        List<LocalDate> dueDates = dueDates(request);
        BigDecimal[] feeAllocation = allocateFee(request);

        BigDecimal balance = Money.round(request.principal(), scale);
        List<ScheduledInstallment> rows = new ArrayList<>(total);
        BigDecimal cumulativePrincipal = Money.zero(scale);
        BigDecimal cumulativeInterest = Money.zero(scale);
        BigDecimal brokenInterest = brokenPeriodInterest(request);

        // Grace rows first: interest-only, or fully deferred with capitalisation.
        for (int k = 1; k <= grace; k++) {
            BigDecimal opening = balance;
            BigDecimal periodInterest = Money.round(balance.multiply(rate, Money.MC), scale);
            if (k == 1 && !Money.isZero(brokenInterest)) {
                periodInterest = Money.floorAtZero(periodInterest.add(brokenInterest), scale);
            }
            BigDecimal fee = feeAllocation[k - 1];
            BigDecimal interestDue;
            if (request.graceType() == GraceType.FULL_GRACE) {
                // Nothing is collected; the accrued interest is capitalised into the balance.
                balance = balance.add(periodInterest);
                interestDue = Money.zero(scale);
            } else {
                interestDue = periodInterest;
                cumulativeInterest = cumulativeInterest.add(interestDue);
            }
            BigDecimal due = interestDue.add(fee);
            rows.add(new ScheduledInstallment(k, dueDates.get(k - 1), opening, Money.zero(scale),
                    interestDue, fee, due, balance, cumulativePrincipal, cumulativeInterest, true));
        }

        int amortising = total - grace;
        BigDecimal installment = annuity(balance, rate, amortising, scale);

        for (int k = grace + 1; k <= total; k++) {
            BigDecimal opening = balance;
            BigDecimal periodInterest = Money.round(balance.multiply(rate, Money.MC), scale);
            BigDecimal chargedInterest = periodInterest;
            if (k == 1 && !Money.isZero(brokenInterest)) {
                // Stub interest rides on top of the first installment: it increases the amount
                // due without eating into the principal component (no negative amortisation).
                chargedInterest = Money.floorAtZero(periodInterest.add(brokenInterest), scale);
            }
            BigDecimal principalPortion;
            if (k == total) {
                principalPortion = balance;
            } else {
                principalPortion = installment.subtract(periodInterest);
                if (Money.isNegative(principalPortion)) {
                    throw new LoanCalculationException("annualInterestRate",
                            "Interest exceeds the level installment; the loan cannot amortise on these terms");
                }
                principalPortion = Money.min(principalPortion, balance);
            }
            balance = Money.round(balance.subtract(principalPortion), scale);
            cumulativePrincipal = cumulativePrincipal.add(principalPortion);
            cumulativeInterest = cumulativeInterest.add(chargedInterest);
            BigDecimal fee = feeAllocation[k - 1];
            BigDecimal due = principalPortion.add(chargedInterest).add(fee);
            rows.add(new ScheduledInstallment(k, dueDates.get(k - 1), opening, principalPortion,
                    chargedInterest, fee, due, balance, cumulativePrincipal, cumulativeInterest, false));
        }

        return assemble(request, rows, installment, brokenInterest);
    }

    // ---------------------------------------------------------------------------- flat

    private LoanSchedule flat(LoanCalculationRequest request) {
        int scale = request.currencyScale();
        int total = request.numberOfInstallments();
        int grace = request.gracePeriods();
        List<LocalDate> dueDates = dueDates(request);
        BigDecimal[] feeAllocation = allocateFee(request);

        BigDecimal principal = Money.round(request.principal(), scale);
        BigDecimal years = BigDecimal.valueOf(total)
                .divide(BigDecimal.valueOf(request.frequency().periodsPerYear()), Money.MC);
        BigDecimal totalInterest = Money.round(principal
                .multiply(request.annualInterestRate(), Money.MC)
                .divide(Money.HUNDRED, Money.MC)
                .multiply(years, Money.MC), scale);

        int amortising = total - grace;
        // Principal is only repaid outside the moratorium. Interest is collected during a
        // PRINCIPAL_GRACE moratorium but deferred (and re-spread) under FULL_GRACE.
        int interestRows = request.graceType() == GraceType.FULL_GRACE ? amortising : total;
        BigDecimal principalPerRow = divideDown(principal, amortising, scale);
        BigDecimal interestPerRow = divideDown(totalInterest, interestRows, scale);

        BigDecimal balance = principal;
        BigDecimal cumulativePrincipal = Money.zero(scale);
        BigDecimal cumulativeInterest = Money.zero(scale);
        List<ScheduledInstallment> rows = new ArrayList<>(total);

        for (int k = 1; k <= total; k++) {
            boolean inGrace = k <= grace;
            BigDecimal opening = balance;
            BigDecimal principalPortion;
            if (inGrace) {
                principalPortion = Money.zero(scale);
            } else if (k == total) {
                principalPortion = balance; // absorbs the rounding residue
            } else {
                principalPortion = principalPerRow;
            }
            BigDecimal interestPortion;
            if (inGrace && request.graceType() == GraceType.FULL_GRACE) {
                interestPortion = Money.zero(scale);
            } else if (k == total) {
                interestPortion = Money.round(totalInterest.subtract(cumulativeInterest), scale);
            } else {
                interestPortion = interestPerRow;
            }
            balance = Money.round(balance.subtract(principalPortion), scale);
            cumulativePrincipal = cumulativePrincipal.add(principalPortion);
            cumulativeInterest = cumulativeInterest.add(interestPortion);
            BigDecimal fee = feeAllocation[k - 1];
            BigDecimal due = principalPortion.add(interestPortion).add(fee);
            rows.add(new ScheduledInstallment(k, dueDates.get(k - 1), opening, principalPortion,
                    interestPortion, fee, due, balance, cumulativePrincipal, cumulativeInterest, inGrace));
        }

        BigDecimal regular = Money.round(principal.add(totalInterest)
                .divide(BigDecimal.valueOf(total), Money.MC), scale);
        return assemble(request, rows, regular, Money.zero(scale));
    }

    // ------------------------------------------------------------------------- helpers

    /**
     * Level annuity payment. Falls back to straight-line principal for interest-free loans,
     * which is also the mathematical limit of the annuity formula as {@code i -> 0}.
     */
    BigDecimal annuity(BigDecimal balance, BigDecimal periodicRate, int periods, int scale) {
        if (periods <= 0) {
            throw new LoanCalculationException("numberOfInstallments",
                    "At least one amortising installment is required");
        }
        if (Money.isZero(periodicRate)) {
            return Money.round(balance.divide(BigDecimal.valueOf(periods), Money.MC), scale);
        }
        BigDecimal onePlusRate = BigDecimal.ONE.add(periodicRate);
        BigDecimal compounded = onePlusRate.pow(periods, Money.MC);
        BigDecimal numerator = balance.multiply(periodicRate, Money.MC).multiply(compounded, Money.MC);
        BigDecimal denominator = compounded.subtract(BigDecimal.ONE);
        if (Money.isZero(denominator)) {
            // Unreachable for rate > 0, but never divide by zero in financial code.
            return Money.round(balance.divide(BigDecimal.valueOf(periods), Money.MC), scale);
        }
        return Money.round(numerator.divide(denominator, Money.MC), scale);
    }

    /**
     * Interest for the stub between the natural first due date and the requested one.
     * Applies to reducing-balance loans only: the flat-rate total is fixed by its formula.
     */
    private BigDecimal brokenPeriodInterest(LoanCalculationRequest request) {
        int scale = request.currencyScale();
        if (request.firstRepaymentDate() == null || request.interestFree()
                || request.interestMethod() == InterestMethod.FLAT) {
            return Money.zero(scale);
        }
        LocalDate naturalFirstDue = request.frequency().next(request.disbursementDate());
        long stubDays = request.dayCount().days(naturalFirstDue, request.firstRepaymentDate());
        if (stubDays == 0) {
            return Money.zero(scale);
        }
        BigDecimal fraction = BigDecimal.valueOf(stubDays)
                .divide(BigDecimal.valueOf(request.dayCount().daysInYear()), Money.MC);
        return Money.round(request.principal()
                .multiply(request.annualInterestRate(), Money.MC)
                .divide(Money.HUNDRED, Money.MC)
                .multiply(fraction, Money.MC), scale);
    }

    /**
     * Due-date calendar. Dates are always generated as multiples of the period from a single
     * anchor, never by stepping off the previous date: stepping would let a clamped month-end
     * drift permanently (31 Jan -&gt; 28 Feb -&gt; 28 Mar -&gt; 28 Apr...), whereas anchoring keeps
     * the loan on its true day-of-month (31 Jan -&gt; 28 Feb -&gt; 31 Mar -&gt; 30 Apr).
     */
    private List<LocalDate> dueDates(LoanCalculationRequest request) {
        // With an explicit first due date the lender has chosen the repayment day, so that
        // date becomes the anchor; otherwise the disbursement date is.
        LocalDate anchor = request.firstRepaymentDate() != null
                ? request.firstRepaymentDate()
                : request.disbursementDate();
        int offset = request.firstRepaymentDate() != null ? 0 : 1;
        List<LocalDate> dates = new ArrayList<>(request.numberOfInstallments());
        for (int k = 0; k < request.numberOfInstallments(); k++) {
            dates.add(request.frequency().next(anchor, k + offset));
        }
        return dates;
    }

    /** Fee amount attributable to each installment, indexed from 0. */
    private BigDecimal[] allocateFee(LoanCalculationRequest request) {
        int scale = request.currencyScale();
        int total = request.numberOfInstallments();
        BigDecimal[] allocation = new BigDecimal[total];
        java.util.Arrays.fill(allocation, Money.zero(scale));
        BigDecimal fee = request.processingFee();
        if (!Money.isPositive(fee)) {
            return allocation;
        }
        switch (request.feeCollection()) {
            case DEDUCT_FROM_DISBURSEMENT -> { /* collected up front, nothing in the schedule */ }
            case ADD_TO_FIRST_INSTALLMENT -> allocation[0] = fee;
            case SPREAD_ACROSS_INSTALLMENTS -> {
                BigDecimal perRow = divideDown(fee, total, scale);
                BigDecimal assigned = Money.zero(scale);
                for (int k = 0; k < total - 1; k++) {
                    allocation[k] = perRow;
                    assigned = assigned.add(perRow);
                }
                allocation[total - 1] = Money.round(fee.subtract(assigned), scale);
            }
        }
        return allocation;
    }

    /** Divides rounding <em>down</em>, so the accumulated residue is always non-negative. */
    private BigDecimal divideDown(BigDecimal amount, int parts, int scale) {
        if (parts <= 0) {
            return Money.zero(scale);
        }
        return amount.divide(BigDecimal.valueOf(parts), scale, java.math.RoundingMode.DOWN);
    }

    private LoanSchedule assemble(LoanCalculationRequest request,
                                  List<ScheduledInstallment> rows,
                                  BigDecimal regularInstallment,
                                  BigDecimal brokenInterest) {
        int scale = request.currencyScale();
        BigDecimal totalInterest = Money.zero(scale);
        BigDecimal totalRepayable = Money.zero(scale);
        for (ScheduledInstallment row : rows) {
            totalInterest = totalInterest.add(row.interest());
            totalRepayable = totalRepayable.add(row.totalDue());
        }
        BigDecimal fee = request.processingFee();
        BigDecimal netDisbursed = request.feeCollection() == FeeCollection.DEDUCT_FROM_DISBURSEMENT
                ? Money.round(request.principal().subtract(fee), scale)
                : Money.round(request.principal(), scale);
        return new LoanSchedule(
                request.interestMethod(),
                request.frequency(),
                Money.round(request.principal(), scale),
                totalInterest,
                fee,
                totalRepayable,
                regularInstallment,
                netDisbursed,
                request.periodicRate(),
                request.effectiveFirstRepaymentDate(),
                rows.get(rows.size() - 1).dueDate(),
                brokenInterest,
                rows);
    }
}

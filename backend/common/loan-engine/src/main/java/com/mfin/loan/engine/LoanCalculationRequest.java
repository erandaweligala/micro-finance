package com.mfin.loan.engine;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Immutable input to the amortisation engine.
 *
 * <p>Amounts are {@link BigDecimal}; {@code annualInterestRate} is a percentage
 * (12.5 means 12.5% p.a.), never a fraction. Build with {@link #builder()}.</p>
 *
 * @param principal            approved principal, must be &gt; 0
 * @param annualInterestRate   nominal annual rate in percent, &gt;= 0 (0 means an interest-free loan)
 * @param numberOfInstallments total number of repayments, &gt;= 1
 * @param frequency            repayment cadence
 * @param interestMethod       flat or reducing balance
 * @param disbursementDate     value date the loan is released
 * @param firstRepaymentDate   first due date; when null it defaults to one full period after disbursement
 * @param graceType            moratorium behaviour
 * @param gracePeriods         number of leading installments covered by the moratorium
 * @param feeType              how {@code feeValue} is quoted
 * @param feeValue             processing fee amount or percentage
 * @param feeCollection        when the fee is collected
 * @param dayCount             convention used for broken-period interest
 * @param currencyScale        decimal places of the loan currency (2 for USD/KES/LKR, 0 for JPY/UGX)
 */
public record LoanCalculationRequest(
        BigDecimal principal,
        BigDecimal annualInterestRate,
        int numberOfInstallments,
        RepaymentFrequency frequency,
        InterestMethod interestMethod,
        LocalDate disbursementDate,
        LocalDate firstRepaymentDate,
        GraceType graceType,
        int gracePeriods,
        FeeType feeType,
        BigDecimal feeValue,
        FeeCollection feeCollection,
        DayCountConvention dayCount,
        int currencyScale
) {

    /** Guards against absurd tenors that would make the amortisation loop pathological. */
    public static final int MAX_INSTALLMENTS = 600;

    /** Rates above this are almost certainly a data-entry error (e.g. a fraction sent as a percent). */
    public static final BigDecimal MAX_ANNUAL_RATE = BigDecimal.valueOf(200);

    public LoanCalculationRequest {
        if (principal == null || principal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new LoanCalculationException("principal", "Principal must be greater than zero");
        }
        if (annualInterestRate == null || annualInterestRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new LoanCalculationException("annualInterestRate", "Interest rate must not be negative");
        }
        if (annualInterestRate.compareTo(MAX_ANNUAL_RATE) > 0) {
            throw new LoanCalculationException("annualInterestRate",
                    "Interest rate must not exceed " + MAX_ANNUAL_RATE + "% per annum");
        }
        if (numberOfInstallments < 1 || numberOfInstallments > MAX_INSTALLMENTS) {
            throw new LoanCalculationException("numberOfInstallments",
                    "Number of installments must be between 1 and " + MAX_INSTALLMENTS);
        }
        if (frequency == null) {
            throw new LoanCalculationException("frequency", "Repayment frequency is required");
        }
        if (interestMethod == null) {
            throw new LoanCalculationException("interestMethod", "Interest method is required");
        }
        if (disbursementDate == null) {
            throw new LoanCalculationException("disbursementDate", "Disbursement date is required");
        }
        if (firstRepaymentDate != null && !firstRepaymentDate.isAfter(disbursementDate)) {
            throw new LoanCalculationException("firstRepaymentDate",
                    "First repayment date must be after the disbursement date");
        }
        if (currencyScale < 0 || currencyScale > 4) {
            throw new LoanCalculationException("currencyScale", "Currency scale must be between 0 and 4");
        }
        graceType = graceType == null ? GraceType.NONE : graceType;
        if (gracePeriods < 0) {
            throw new LoanCalculationException("gracePeriods", "Grace periods must not be negative");
        }
        if (graceType != GraceType.NONE && gracePeriods >= numberOfInstallments) {
            throw new LoanCalculationException("gracePeriods",
                    "Grace periods must be fewer than the number of installments");
        }
        if (graceType == GraceType.NONE) {
            gracePeriods = 0;
        }
        if (feeValue != null && feeValue.compareTo(BigDecimal.ZERO) < 0) {
            throw new LoanCalculationException("feeValue", "Processing fee must not be negative");
        }
        feeValue = feeValue == null ? BigDecimal.ZERO : feeValue;
        feeType = feeType == null ? FeeType.FLAT_AMOUNT : feeType;
        feeCollection = feeCollection == null ? FeeCollection.DEDUCT_FROM_DISBURSEMENT : feeCollection;
        dayCount = dayCount == null ? DayCountConvention.ACTUAL_365 : dayCount;
        if (feeType == FeeType.PERCENT_OF_PRINCIPAL && feeValue.compareTo(Money.HUNDRED) > 0) {
            throw new LoanCalculationException("feeValue", "Percentage fee must not exceed 100%");
        }
    }

    /** Periodic interest rate as a fraction, e.g. 12% p.a. monthly -&gt; 0.01. */
    public BigDecimal periodicRate() {
        return annualInterestRate
                .divide(BigDecimal.valueOf(frequency.periodsPerYear()), Money.MC)
                .divide(Money.HUNDRED, Money.MC);
    }

    /** The first due date actually used: the supplied one, or one full period after disbursement. */
    public LocalDate effectiveFirstRepaymentDate() {
        return firstRepaymentDate != null ? firstRepaymentDate : frequency.next(disbursementDate);
    }

    /** Absolute processing fee in currency units, rounded to the currency scale. */
    public BigDecimal processingFee() {
        BigDecimal raw = feeType == FeeType.PERCENT_OF_PRINCIPAL
                ? principal.multiply(feeValue, Money.MC).divide(Money.HUNDRED, Money.MC)
                : feeValue;
        return Money.round(raw, currencyScale);
    }

    public boolean interestFree() {
        return annualInterestRate.compareTo(BigDecimal.ZERO) == 0;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder with production-sensible defaults. */
    public static final class Builder {
        private BigDecimal principal;
        private BigDecimal annualInterestRate = BigDecimal.ZERO;
        private int numberOfInstallments;
        private RepaymentFrequency frequency = RepaymentFrequency.MONTHLY;
        private InterestMethod interestMethod = InterestMethod.REDUCING_BALANCE;
        private LocalDate disbursementDate = LocalDate.now();
        private LocalDate firstRepaymentDate;
        private GraceType graceType = GraceType.NONE;
        private int gracePeriods;
        private FeeType feeType = FeeType.FLAT_AMOUNT;
        private BigDecimal feeValue = BigDecimal.ZERO;
        private FeeCollection feeCollection = FeeCollection.DEDUCT_FROM_DISBURSEMENT;
        private DayCountConvention dayCount = DayCountConvention.ACTUAL_365;
        private int currencyScale = Money.DEFAULT_SCALE;

        public Builder principal(BigDecimal v) { this.principal = v; return this; }
        public Builder principal(String v) { this.principal = new BigDecimal(v); return this; }
        public Builder annualInterestRate(BigDecimal v) { this.annualInterestRate = v; return this; }
        public Builder annualInterestRate(String v) { this.annualInterestRate = new BigDecimal(v); return this; }
        public Builder numberOfInstallments(int v) { this.numberOfInstallments = v; return this; }
        public Builder frequency(RepaymentFrequency v) { this.frequency = v; return this; }
        public Builder interestMethod(InterestMethod v) { this.interestMethod = v; return this; }
        public Builder disbursementDate(LocalDate v) { this.disbursementDate = v; return this; }
        public Builder firstRepaymentDate(LocalDate v) { this.firstRepaymentDate = v; return this; }
        public Builder graceType(GraceType v) { this.graceType = v; return this; }
        public Builder gracePeriods(int v) { this.gracePeriods = v; return this; }
        public Builder feeType(FeeType v) { this.feeType = v; return this; }
        public Builder feeValue(BigDecimal v) { this.feeValue = v; return this; }
        public Builder feeCollection(FeeCollection v) { this.feeCollection = v; return this; }
        public Builder dayCount(DayCountConvention v) { this.dayCount = v; return this; }
        public Builder currencyScale(int v) { this.currencyScale = v; return this; }

        public LoanCalculationRequest build() {
            return new LoanCalculationRequest(principal, annualInterestRate, numberOfInstallments,
                    frequency, interestMethod, disbursementDate, firstRepaymentDate, graceType,
                    gracePeriods, feeType, feeValue, feeCollection, dayCount, currencyScale);
        }
    }
}

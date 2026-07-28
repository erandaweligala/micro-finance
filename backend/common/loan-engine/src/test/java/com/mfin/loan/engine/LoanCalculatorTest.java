package com.mfin.loan.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class LoanCalculatorTest {

    private static final LocalDate DISBURSED = LocalDate.of(2026, 1, 15);

    private final LoanCalculator calculator = new LoanCalculator();

    private LoanCalculationRequest.Builder base() {
        return LoanCalculationRequest.builder()
                .principal("100000")
                .annualInterestRate("12")
                .numberOfInstallments(12)
                .frequency(RepaymentFrequency.MONTHLY)
                .interestMethod(InterestMethod.REDUCING_BALANCE)
                .disbursementDate(DISBURSED);
    }

    /** Every schedule, whatever the terms, must satisfy these invariants. */
    private void assertScheduleIsCoherent(LoanSchedule schedule) {
        BigDecimal principalSum = BigDecimal.ZERO;
        BigDecimal interestSum = BigDecimal.ZERO;
        BigDecimal dueSum = BigDecimal.ZERO;
        BigDecimal previousClosing = schedule.principal();
        int expectedNumber = 1;

        for (ScheduledInstallment row : schedule.installments()) {
            assertThat(row.installmentNumber()).isEqualTo(expectedNumber++);
            assertThat(row.principal()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(row.interest()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            // totalDue is exactly its components
            assertThat(row.totalDue())
                    .isEqualByComparingTo(row.principal().add(row.interest()).add(row.fee()));
            // the balance walk is continuous
            assertThat(row.openingBalance()).isEqualByComparingTo(previousClosing);
            if (row.graceInstallment() && schedule.installments().get(0).graceInstallment()) {
                // FULL_GRACE capitalises, so closing may exceed opening during the moratorium
                assertThat(row.closingBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            } else {
                assertThat(row.closingBalance())
                        .isEqualByComparingTo(row.openingBalance().subtract(row.principal()));
            }
            principalSum = principalSum.add(row.principal());
            interestSum = interestSum.add(row.interest());
            dueSum = dueSum.add(row.totalDue());
            previousClosing = row.closingBalance();
        }

        assertThat(previousClosing).as("loan must close at zero").isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(interestSum).as("interest components sum to headline interest")
                .isEqualByComparingTo(schedule.totalInterest());
        assertThat(dueSum).as("installments sum to total repayable")
                .isEqualByComparingTo(schedule.totalRepayable());
    }

    @Nested
    @DisplayName("Reducing balance")
    class ReducingBalance {

        @Test
        @DisplayName("matches the annuity formula: 100,000 @ 12% over 12 months = 8,884.88/month")
        void standardAnnuity() {
            LoanSchedule schedule = calculator.generate(base().build());

            // i = 0.01, installment = 100000*0.01*1.01^12 / (1.01^12 - 1)
            assertThat(schedule.regularInstallment()).isEqualByComparingTo("8884.88");
            assertThat(schedule.totalInterest()).isEqualByComparingTo("6618.53");
            assertThat(schedule.totalRepayable()).isEqualByComparingTo("106618.53");
            assertThat(schedule.numberOfInstallments()).isEqualTo(12);
            assertThat(schedule.periodicRate()).isEqualByComparingTo("0.01");
            assertScheduleIsCoherent(schedule);
        }

        @Test
        @DisplayName("first installment splits interest before principal")
        void firstInstallmentBreakdown() {
            LoanSchedule schedule = calculator.generate(base().build());
            ScheduledInstallment first = schedule.installments().get(0);

            assertThat(first.dueDate()).isEqualTo(LocalDate.of(2026, 2, 15));
            assertThat(first.openingBalance()).isEqualByComparingTo("100000.00");
            assertThat(first.interest()).isEqualByComparingTo("1000.00");   // 100000 * 1%
            assertThat(first.principal()).isEqualByComparingTo("7884.88");
            assertThat(first.closingBalance()).isEqualByComparingTo("92115.12");
        }

        @Test
        @DisplayName("interest falls and principal rises across the schedule")
        void amortisationCurve() {
            LoanSchedule schedule = calculator.generate(base().build());
            var rows = schedule.installments();

            for (int k = 1; k < rows.size(); k++) {
                assertThat(rows.get(k).interest())
                        .as("interest is monotonically decreasing")
                        .isLessThanOrEqualTo(rows.get(k - 1).interest());
                assertThat(rows.get(k).principal())
                        .as("principal is monotonically increasing")
                        .isGreaterThanOrEqualTo(rows.get(k - 1).principal());
            }
        }

        @Test
        @DisplayName("a single-installment loan repays principal plus one period of interest")
        void singleInstallment() {
            LoanSchedule schedule = calculator.generate(base().numberOfInstallments(1).build());

            assertThat(schedule.totalInterest()).isEqualByComparingTo("1000.00");
            assertThat(schedule.totalRepayable()).isEqualByComparingTo("101000.00");
            assertScheduleIsCoherent(schedule);
        }

        @Test
        @DisplayName("long tenors stay coherent to the cent (240 months)")
        void longTenor() {
            LoanSchedule schedule = calculator.generate(base()
                    .principal("2500000")
                    .annualInterestRate("18.75")
                    .numberOfInstallments(240)
                    .build());

            assertThat(schedule.numberOfInstallments()).isEqualTo(240);
            assertThat(schedule.maturityDate()).isEqualTo(LocalDate.of(2046, 1, 15));
            assertScheduleIsCoherent(schedule);
        }
    }

    @Nested
    @DisplayName("Flat rate")
    class Flat {

        @Test
        @DisplayName("100,000 @ 12% flat over 12 months = 12,000 interest, 9,333.33/month")
        void standardFlat() {
            LoanSchedule schedule = calculator.generate(base()
                    .interestMethod(InterestMethod.FLAT)
                    .build());

            assertThat(schedule.totalInterest()).isEqualByComparingTo("12000.00");
            assertThat(schedule.regularInstallment()).isEqualByComparingTo("9333.33");
            assertThat(schedule.totalRepayable()).isEqualByComparingTo("112000.00");
            assertScheduleIsCoherent(schedule);
        }

        @Test
        @DisplayName("flat interest scales with the tenor in years, not the installment count")
        void twoYearFlat() {
            LoanSchedule schedule = calculator.generate(base()
                    .interestMethod(InterestMethod.FLAT)
                    .numberOfInstallments(24)
                    .build());

            // 100000 * 12% * 2 years
            assertThat(schedule.totalInterest()).isEqualByComparingTo("24000.00");
            assertScheduleIsCoherent(schedule);
        }

        @Test
        @DisplayName("flat is more expensive than reducing balance at the same nominal rate")
        void flatCostsMoreThanReducing() {
            LoanSchedule flat = calculator.generate(base().interestMethod(InterestMethod.FLAT).build());
            LoanSchedule reducing = calculator.generate(base().build());

            assertThat(flat.totalInterest()).isGreaterThan(reducing.totalInterest());
        }

        @Test
        @DisplayName("indivisible amounts put the residue on the final installment only")
        void roundingResidueLandsOnLastRow() {
            LoanSchedule schedule = calculator.generate(base()
                    .principal("10000")
                    .annualInterestRate("10")
                    .interestMethod(InterestMethod.FLAT)
                    .numberOfInstallments(7)
                    .frequency(RepaymentFrequency.MONTHLY)
                    .build());

            assertScheduleIsCoherent(schedule);
            var rows = schedule.installments();
            for (int k = 0; k < rows.size() - 1; k++) {
                assertThat(rows.get(k).totalDue())
                        .as("every non-final installment is level")
                        .isEqualByComparingTo(rows.get(0).totalDue());
            }
        }
    }

    @Nested
    @DisplayName("Zero interest")
    class ZeroInterest {

        @ParameterizedTest
        @EnumSource(InterestMethod.class)
        @DisplayName("an interest-free loan repays principal only, under either method")
        void interestFree(InterestMethod method) {
            LoanSchedule schedule = calculator.generate(base()
                    .annualInterestRate("0")
                    .interestMethod(method)
                    .numberOfInstallments(10)
                    .build());

            assertThat(schedule.totalInterest()).isEqualByComparingTo("0.00");
            assertThat(schedule.totalRepayable()).isEqualByComparingTo("100000.00");
            assertThat(schedule.regularInstallment()).isEqualByComparingTo("10000.00");
            assertScheduleIsCoherent(schedule);
        }

        @Test
        @DisplayName("zero-interest with an indivisible principal still closes at zero")
        void interestFreeIndivisible() {
            LoanSchedule schedule = calculator.generate(base()
                    .principal("1000")
                    .annualInterestRate("0")
                    .numberOfInstallments(3)
                    .build());

            assertScheduleIsCoherent(schedule);
            assertThat(schedule.installments().get(0).principal()).isEqualByComparingTo("333.33");
            assertThat(schedule.installments().get(2).principal()).isEqualByComparingTo("333.34");
        }
    }

    @Nested
    @DisplayName("Grace periods")
    class Grace {

        @Test
        @DisplayName("interest-only grace defers principal and raises later installments")
        void principalGrace() {
            LoanSchedule schedule = calculator.generate(base()
                    .graceType(GraceType.PRINCIPAL_GRACE)
                    .gracePeriods(3)
                    .build());

            var rows = schedule.installments();
            for (int k = 0; k < 3; k++) {
                assertThat(rows.get(k).principal()).isEqualByComparingTo("0.00");
                assertThat(rows.get(k).interest()).isEqualByComparingTo("1000.00");
                assertThat(rows.get(k).closingBalance()).isEqualByComparingTo("100000.00");
                assertThat(rows.get(k).graceInstallment()).isTrue();
            }
            assertThat(rows.get(3).principal()).isGreaterThan(BigDecimal.ZERO);
            assertThat(schedule.regularInstallment())
                    .as("principal is compressed into 9 installments")
                    .isGreaterThan(new BigDecimal("8884.88"));
            assertScheduleIsCoherent(schedule);
        }

        @Test
        @DisplayName("full moratorium collects nothing and capitalises the accrued interest")
        void fullGrace() {
            LoanSchedule schedule = calculator.generate(base()
                    .graceType(GraceType.FULL_GRACE)
                    .gracePeriods(3)
                    .build());

            var rows = schedule.installments();
            for (int k = 0; k < 3; k++) {
                assertThat(rows.get(k).totalDue()).isEqualByComparingTo("0.00");
            }
            assertThat(rows.get(2).closingBalance())
                    .as("three months of capitalised interest: 100000 * 1.01^3")
                    .isEqualByComparingTo("103030.10");
            assertScheduleIsCoherent(schedule);
        }
    }

    @Nested
    @DisplayName("Partial (broken) periods")
    class BrokenPeriod {

        @Test
        @DisplayName("a later first due date charges stub interest on the first installment")
        void stubInterestCharged() {
            LoanSchedule withStub = calculator.generate(base()
                    .firstRepaymentDate(LocalDate.of(2026, 3, 1))   // 14 days past the natural 15 Feb
                    .build());
            LoanSchedule withoutStub = calculator.generate(base().build());

            // 100000 * 12% * 14/365
            assertThat(withStub.brokenPeriodInterest()).isEqualByComparingTo("460.27");
            assertThat(withStub.installments().get(0).interest())
                    .isEqualByComparingTo(withoutStub.installments().get(0).interest().add(new BigDecimal("460.27")));
            assertThat(withStub.installments().get(0).principal())
                    .as("stub interest must not cause negative amortisation")
                    .isEqualByComparingTo(withoutStub.installments().get(0).principal());
            assertScheduleIsCoherent(withStub);
        }

        @Test
        @DisplayName("an exact-period first due date charges no stub interest")
        void noStubWhenAligned() {
            LoanSchedule schedule = calculator.generate(base()
                    .firstRepaymentDate(LocalDate.of(2026, 2, 15))
                    .build());

            assertThat(schedule.brokenPeriodInterest()).isEqualByComparingTo("0.00");
        }

        @ParameterizedTest
        @EnumSource(DayCountConvention.class)
        @DisplayName("every day-count convention produces a coherent schedule")
        void allConventions(DayCountConvention convention) {
            LoanSchedule schedule = calculator.generate(base()
                    .firstRepaymentDate(LocalDate.of(2026, 3, 20))
                    .dayCount(convention)
                    .build());

            assertThat(schedule.brokenPeriodInterest()).isGreaterThan(BigDecimal.ZERO);
            assertScheduleIsCoherent(schedule);
        }
    }

    @Nested
    @DisplayName("Processing fees")
    class Fees {

        @Test
        @DisplayName("a fee deducted at disbursement reduces cash out, not the repayments")
        void deductedFromDisbursement() {
            LoanSchedule schedule = calculator.generate(base()
                    .feeType(FeeType.PERCENT_OF_PRINCIPAL)
                    .feeValue(new BigDecimal("2"))
                    .feeCollection(FeeCollection.DEDUCT_FROM_DISBURSEMENT)
                    .build());

            assertThat(schedule.totalFees()).isEqualByComparingTo("2000.00");
            assertThat(schedule.netDisbursedAmount()).isEqualByComparingTo("98000.00");
            assertThat(schedule.totalRepayable()).isEqualByComparingTo("106618.53");
            assertScheduleIsCoherent(schedule);
        }

        @Test
        @DisplayName("a fee added to the first installment is payable on top of it")
        void addedToFirstInstallment() {
            LoanSchedule schedule = calculator.generate(base()
                    .feeValue(new BigDecimal("1500"))
                    .feeCollection(FeeCollection.ADD_TO_FIRST_INSTALLMENT)
                    .build());

            assertThat(schedule.netDisbursedAmount()).isEqualByComparingTo("100000.00");
            assertThat(schedule.installments().get(0).fee()).isEqualByComparingTo("1500.00");
            assertThat(schedule.installments().get(1).fee()).isEqualByComparingTo("0.00");
            assertThat(schedule.totalRepayable()).isEqualByComparingTo("108118.53");
            assertScheduleIsCoherent(schedule);
        }

        @Test
        @DisplayName("a spread fee is split across installments with the residue on the last")
        void spreadAcrossInstallments() {
            LoanSchedule schedule = calculator.generate(base()
                    .feeValue(new BigDecimal("1000"))
                    .feeCollection(FeeCollection.SPREAD_ACROSS_INSTALLMENTS)
                    .numberOfInstallments(3)
                    .build());

            var rows = schedule.installments();
            assertThat(rows.get(0).fee()).isEqualByComparingTo("333.33");
            assertThat(rows.get(1).fee()).isEqualByComparingTo("333.33");
            assertThat(rows.get(2).fee()).isEqualByComparingTo("333.34");
            assertScheduleIsCoherent(schedule);
        }
    }

    @Nested
    @DisplayName("Frequencies and currencies")
    class FrequenciesAndCurrencies {

        @ParameterizedTest
        @EnumSource(RepaymentFrequency.class)
        @DisplayName("every cadence amortises to zero")
        void everyFrequency(RepaymentFrequency frequency) {
            LoanSchedule schedule = calculator.generate(base()
                    .frequency(frequency)
                    .numberOfInstallments(6)
                    .build());

            assertThat(schedule.periodicRate()).isEqualByComparingTo(
                    new BigDecimal("12").divide(BigDecimal.valueOf(frequency.periodsPerYear()), Money.MC)
                            .divide(Money.HUNDRED, Money.MC));
            assertScheduleIsCoherent(schedule);
        }

        @Test
        @DisplayName("weekly due dates advance by seven days")
        void weeklyDueDates() {
            LoanSchedule schedule = calculator.generate(base()
                    .frequency(RepaymentFrequency.WEEKLY)
                    .numberOfInstallments(4)
                    .build());

            assertThat(schedule.installments().get(0).dueDate()).isEqualTo(LocalDate.of(2026, 1, 22));
            assertThat(schedule.installments().get(3).dueDate()).isEqualTo(LocalDate.of(2026, 2, 12));
        }

        @Test
        @DisplayName("month-end disbursement clamps the due day (31 Jan -> 28 Feb)")
        void monthEndClamping() {
            LoanSchedule schedule = calculator.generate(base()
                    .disbursementDate(LocalDate.of(2026, 1, 31))
                    .numberOfInstallments(3)
                    .build());

            assertThat(schedule.installments().get(0).dueDate()).isEqualTo(LocalDate.of(2026, 2, 28));
            assertThat(schedule.installments().get(1).dueDate()).isEqualTo(LocalDate.of(2026, 3, 31));
        }

        @Test
        @DisplayName("zero-decimal currencies round to whole units")
        void zeroDecimalCurrency() {
            LoanSchedule schedule = calculator.generate(base()
                    .principal("1000000")
                    .currencyScale(0)
                    .build());

            assertThat(schedule.regularInstallment().scale()).isZero();
            schedule.installments().forEach(row -> assertThat(row.totalDue().scale()).isZero());
            assertScheduleIsCoherent(schedule);
        }
    }

    @Nested
    @DisplayName("Input validation")
    class Validation {

        @ParameterizedTest
        @CsvSource({"0", "-1", "-0.01"})
        @DisplayName("principal must be positive")
        void rejectsNonPositivePrincipal(String principal) {
            assertThatThrownBy(() -> base().principal(principal).build())
                    .isInstanceOf(LoanCalculationException.class)
                    .hasMessageContaining("Principal");
        }

        @Test
        @DisplayName("negative interest is rejected")
        void rejectsNegativeRate() {
            assertThatThrownBy(() -> base().annualInterestRate("-5").build())
                    .isInstanceOf(LoanCalculationException.class);
        }

        @Test
        @DisplayName("absurd interest rates are rejected as data-entry errors")
        void rejectsAbsurdRate() {
            assertThatThrownBy(() -> base().annualInterestRate("500").build())
                    .isInstanceOf(LoanCalculationException.class)
                    .hasMessageContaining("200");
        }

        @ParameterizedTest
        @CsvSource({"0", "-3", "601"})
        @DisplayName("installment count must be within bounds")
        void rejectsBadInstallmentCount(int count) {
            assertThatThrownBy(() -> base().numberOfInstallments(count).build())
                    .isInstanceOf(LoanCalculationException.class);
        }

        @Test
        @DisplayName("the first repayment date must follow disbursement")
        void rejectsFirstRepaymentBeforeDisbursement() {
            assertThatThrownBy(() -> base().firstRepaymentDate(DISBURSED.minusDays(1)).build())
                    .isInstanceOf(LoanCalculationException.class);
        }

        @Test
        @DisplayName("grace cannot consume the whole tenor")
        void rejectsGraceCoveringWholeTenor() {
            assertThatThrownBy(() -> base()
                    .graceType(GraceType.PRINCIPAL_GRACE)
                    .gracePeriods(12)
                    .numberOfInstallments(12)
                    .build())
                    .isInstanceOf(LoanCalculationException.class)
                    .hasMessageContaining("fewer");
        }

        @Test
        @DisplayName("a null request is rejected rather than NPE'd")
        void rejectsNullRequest() {
            assertThatThrownBy(() -> calculator.generate(null))
                    .isInstanceOf(LoanCalculationException.class);
        }

        @Test
        @DisplayName("the exception names the offending field for API error mapping")
        void exceptionCarriesFieldName() {
            assertThatThrownBy(() -> base().principal("0").build())
                    .isInstanceOfSatisfying(LoanCalculationException.class,
                            ex -> assertThat(ex.field()).isEqualTo("principal"));
        }
    }

    @Nested
    @DisplayName("Precision")
    class Precision {

        @Test
        @DisplayName("recurring decimals do not leak: 1/3-style rates stay balanced")
        void recurringDecimalRate() {
            LoanSchedule schedule = calculator.generate(base()
                    .principal("33333.33")
                    .annualInterestRate("13.333")
                    .numberOfInstallments(17)
                    .build());

            assertScheduleIsCoherent(schedule);
        }

        @Test
        @DisplayName("very small principals still amortise exactly")
        void tinyPrincipal() {
            LoanSchedule schedule = calculator.generate(base()
                    .principal("1")
                    .numberOfInstallments(3)
                    .build());

            assertScheduleIsCoherent(schedule);
        }

        @Test
        @DisplayName("the level installment matches a double-precision reference within a cent")
        void matchesReferenceImplementation() {
            LoanSchedule schedule = calculator.generate(base()
                    .principal("875432.10")
                    .annualInterestRate("14.25")
                    .numberOfInstallments(36)
                    .build());

            double i = 0.1425 / 12;
            double reference = 875432.10 * i * Math.pow(1 + i, 36) / (Math.pow(1 + i, 36) - 1);
            assertThat(schedule.regularInstallment().doubleValue()).isCloseTo(reference, within(0.01));
        }
    }
}

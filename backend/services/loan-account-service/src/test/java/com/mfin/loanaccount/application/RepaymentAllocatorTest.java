package com.mfin.loanaccount.application;

import com.mfin.loanaccount.domain.AllocationOrder;
import com.mfin.loanaccount.domain.ScheduleInstallment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RepaymentAllocatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

    private final RepaymentAllocator allocator = new RepaymentAllocator();

    /** Builds an installment with the given dues; ids are assigned so allocations can be matched. */
    private ScheduleInstallment installment(int number, LocalDate dueDate, String principal,
                                            String interest, String fee, String penalty) {
        ScheduleInstallment installment =
                new ScheduleInstallment(UUID.randomUUID(), number, dueDate);
        installment.setId(UUID.randomUUID());
        installment.setAmounts(new BigDecimal("1000.00"), new BigDecimal(principal),
                new BigDecimal(interest), new BigDecimal(fee), BigDecimal.ZERO, false);
        if (new BigDecimal(penalty).signum() > 0) {
            installment.addPenalty(new BigDecimal(penalty));
        }
        return installment;
    }

    /** Two overdue installments and two future ones - the common arrears shape. */
    private List<ScheduleInstallment> schedule() {
        return List.of(
                installment(1, TODAY.minusMonths(2), "800.00", "200.00", "0.00", "50.00"),
                installment(2, TODAY.minusMonths(1), "800.00", "200.00", "0.00", "25.00"),
                installment(3, TODAY.plusMonths(1), "800.00", "200.00", "0.00", "0.00"),
                installment(4, TODAY.plusMonths(2), "800.00", "200.00", "0.00", "0.00"));
    }

    @Nested
    @DisplayName("Allocation order")
    class Order {

        @Test
        @DisplayName("penalties are settled before interest, and interest before principal")
        void defaultOrder() {
            var plan = allocator.allocate(schedule(), new BigDecimal("300.00"), TODAY,
                    AllocationOrder.PENALTY_FEE_INTEREST_PRINCIPAL);

            // 50 penalty, then 200 interest, then 50 principal of installment 1
            assertThat(plan.penalty()).isEqualByComparingTo("50.00");
            assertThat(plan.interest()).isEqualByComparingTo("200.00");
            assertThat(plan.principal()).isEqualByComparingTo("50.00");
            assertThat(plan.applied()).isEqualByComparingTo("300.00");
            assertThat(plan.excess()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("a borrower-friendly policy reduces principal first")
        void principalFirst() {
            var plan = allocator.allocate(schedule(), new BigDecimal("300.00"), TODAY,
                    AllocationOrder.PRINCIPAL_INTEREST_FEE_PENALTY);

            assertThat(plan.principal()).isEqualByComparingTo("300.00");
            assertThat(plan.interest()).isEqualByComparingTo("0.00");
            assertThat(plan.penalty()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("interest-first policy outranks penalties")
        void interestFirst() {
            var plan = allocator.allocate(schedule(), new BigDecimal("250.00"), TODAY,
                    AllocationOrder.INTEREST_PRINCIPAL_FEE_PENALTY);

            assertThat(plan.interest()).isEqualByComparingTo("200.00");
            assertThat(plan.principal()).isEqualByComparingTo("50.00");
            assertThat(plan.penalty()).isEqualByComparingTo("0.00");
        }
    }

    @Nested
    @DisplayName("Oldest debt first")
    class OldestFirst {

        @Test
        @DisplayName("the earliest overdue installment is settled before the next one is touched")
        void settlesOldestBeforeNext() {
            var installments = schedule();
            // Installment 1 owes 1050 in total.
            var plan = allocator.allocate(installments, new BigDecimal("1050.00"), TODAY,
                    AllocationOrder.PENALTY_FEE_INTEREST_PRINCIPAL);

            assertThat(plan.allocations()).hasSize(1);
            assertThat(plan.allocations().get(0).installmentNumber()).isEqualTo(1);
            assertThat(plan.allocations().get(0).total()).isEqualByComparingTo("1050.00");
        }

        @Test
        @DisplayName("a payment spanning two installments clears the first entirely")
        void spillsIntoTheNextInstallment() {
            var plan = allocator.allocate(schedule(), new BigDecimal("1500.00"), TODAY,
                    AllocationOrder.PENALTY_FEE_INTEREST_PRINCIPAL);

            assertThat(plan.allocations()).hasSize(2);
            assertThat(plan.allocations().get(0).total()).isEqualByComparingTo("1050.00");
            assertThat(plan.allocations().get(1).installmentNumber()).isEqualTo(2);
            assertThat(plan.allocations().get(1).total()).isEqualByComparingTo("450.00");
            // The second installment's buckets are filled in policy order: penalty then interest.
            assertThat(plan.allocations().get(1).penalty()).isEqualByComparingTo("25.00");
            assertThat(plan.allocations().get(1).interest()).isEqualByComparingTo("200.00");
            assertThat(plan.allocations().get(1).principal()).isEqualByComparingTo("225.00");
        }
    }

    @Nested
    @DisplayName("Partial, advance and excess payments")
    class PaymentShapes {

        @Test
        @DisplayName("a partial payment leaves the installment outstanding")
        void partialPayment() {
            var plan = allocator.allocate(schedule(), new BigDecimal("100.00"), TODAY,
                    AllocationOrder.PENALTY_FEE_INTEREST_PRINCIPAL);

            assertThat(plan.applied()).isEqualByComparingTo("100.00");
            assertThat(plan.excess()).isEqualByComparingTo("0.00");
            assertThat(plan.allocations()).hasSize(1);
        }

        @Test
        @DisplayName("money left after all arrears pays future installments in date order")
        void advancePayment() {
            // Arrears total 2075 (1050 + 1025); paying 3000 leaves 925 for installment 3.
            var plan = allocator.allocate(schedule(), new BigDecimal("3000.00"), TODAY,
                    AllocationOrder.PENALTY_FEE_INTEREST_PRINCIPAL);

            assertThat(plan.advanceApplied()).isEqualByComparingTo("925.00");
            var advanceRow = plan.allocations().stream()
                    .filter(RepaymentAllocator.InstallmentAllocation::advance)
                    .findFirst()
                    .orElseThrow();
            assertThat(advanceRow.installmentNumber()).isEqualTo(3);
            assertThat(advanceRow.total()).isEqualByComparingTo("925.00");
            assertThat(plan.excess()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("advance amounts are counted once, not added on top of the bucket totals")
        void advanceIsNotDoubleCounted() {
            var plan = allocator.allocate(schedule(), new BigDecimal("3000.00"), TODAY,
                    AllocationOrder.PENALTY_FEE_INTEREST_PRINCIPAL);

            // The whole payment is accounted for exactly once.
            assertThat(plan.total()).isEqualByComparingTo("3000.00");
            assertThat(plan.applied().add(plan.excess())).isEqualByComparingTo("3000.00");
            // advanceApplied is a subset of applied, never an addition to it.
            assertThat(plan.advanceApplied()).isLessThanOrEqualTo(plan.applied());
        }

        @Test
        @DisplayName("paying off the whole loan leaves nothing outstanding and no excess")
        void fullSettlement() {
            // 4 installments * 1000 + 75 penalty
            var plan = allocator.allocate(schedule(), new BigDecimal("4075.00"), TODAY,
                    AllocationOrder.PENALTY_FEE_INTEREST_PRINCIPAL);

            assertThat(plan.applied()).isEqualByComparingTo("4075.00");
            assertThat(plan.excess()).isEqualByComparingTo("0.00");
            assertThat(plan.principal()).isEqualByComparingTo("3200.00");
            assertThat(plan.interest()).isEqualByComparingTo("800.00");
            assertThat(plan.penalty()).isEqualByComparingTo("75.00");
        }

        @Test
        @DisplayName("overpayment beyond the whole contract becomes a credit, not a phantom allocation")
        void overpaymentBecomesCredit() {
            var plan = allocator.allocate(schedule(), new BigDecimal("5000.00"), TODAY,
                    AllocationOrder.PENALTY_FEE_INTEREST_PRINCIPAL);

            assertThat(plan.applied()).isEqualByComparingTo("4075.00");
            assertThat(plan.excess()).isEqualByComparingTo("925.00");
            assertThat(plan.total()).isEqualByComparingTo("5000.00");
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("a settled installment is skipped entirely")
        void skipsSettledInstallments() {
            var installments = schedule();
            ScheduleInstallment first = installments.get(0);
            first.allocate(new BigDecimal("800.00"), new BigDecimal("200.00"), BigDecimal.ZERO,
                    new BigDecimal("50.00"), TODAY);
            assertThat(first.isSettled()).isTrue();

            var plan = allocator.allocate(installments, new BigDecimal("100.00"), TODAY,
                    AllocationOrder.PENALTY_FEE_INTEREST_PRINCIPAL);

            assertThat(plan.allocations()).hasSize(1);
            assertThat(plan.allocations().get(0).installmentNumber()).isEqualTo(2);
        }

        @Test
        @DisplayName("with nothing due, the whole payment pays ahead")
        void nothingDueYet() {
            List<ScheduleInstallment> future = List.of(
                    installment(1, TODAY.plusMonths(1), "800.00", "200.00", "0.00", "0.00"),
                    installment(2, TODAY.plusMonths(2), "800.00", "200.00", "0.00", "0.00"));

            var plan = allocator.allocate(future, new BigDecimal("1000.00"), TODAY,
                    AllocationOrder.PENALTY_FEE_INTEREST_PRINCIPAL);

            assertThat(plan.advanceApplied()).isEqualByComparingTo("1000.00");
            assertThat(plan.allocations()).allMatch(RepaymentAllocator.InstallmentAllocation::advance);
        }

        @Test
        @DisplayName("an installment due exactly today counts as due, not future")
        void dueTodayIsDue() {
            List<ScheduleInstallment> today = List.of(
                    installment(1, TODAY, "800.00", "200.00", "0.00", "0.00"));

            var plan = allocator.allocate(today, new BigDecimal("500.00"), TODAY,
                    AllocationOrder.PENALTY_FEE_INTEREST_PRINCIPAL);

            assertThat(plan.advanceApplied()).isEqualByComparingTo("0.00");
            assertThat(plan.allocations().get(0).advance()).isFalse();
        }

        @Test
        @DisplayName("a fully settled loan turns the whole payment into a credit")
        void everythingAlreadyPaid() {
            var installments = schedule();
            installments.forEach(installment -> installment.allocate(
                    installment.outstandingPrincipal(), installment.outstandingInterest(),
                    installment.outstandingFee(), installment.outstandingPenalty(), TODAY));

            var plan = allocator.allocate(installments, new BigDecimal("500.00"), TODAY,
                    AllocationOrder.PENALTY_FEE_INTEREST_PRINCIPAL);

            assertThat(plan.applied()).isEqualByComparingTo("0.00");
            assertThat(plan.excess()).isEqualByComparingTo("500.00");
            assertThat(plan.allocations()).isEmpty();
        }

        @Test
        @DisplayName("fees are settled ahead of interest under the default policy")
        void feesBeforeInterest() {
            List<ScheduleInstallment> withFee = List.of(
                    installment(1, TODAY.minusMonths(1), "800.00", "200.00", "75.00", "25.00"));

            var plan = allocator.allocate(withFee, new BigDecimal("120.00"), TODAY,
                    AllocationOrder.PENALTY_FEE_INTEREST_PRINCIPAL);

            assertThat(plan.penalty()).isEqualByComparingTo("25.00");
            assertThat(plan.fee()).isEqualByComparingTo("75.00");
            assertThat(plan.interest()).isEqualByComparingTo("20.00");
            assertThat(plan.principal()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("the plan never allocates more than the payment")
        void neverOverAllocates() {
            for (String amount : List.of("0.01", "1.00", "999.99", "2100.00", "4074.99", "10000.00")) {
                var plan = allocator.allocate(schedule(), new BigDecimal(amount), TODAY,
                        AllocationOrder.PENALTY_FEE_INTEREST_PRINCIPAL);
                assertThat(plan.total())
                        .as("payment of %s must be fully and exactly accounted for", amount)
                        .isEqualByComparingTo(amount);
            }
        }
    }
}

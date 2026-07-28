package com.mfin.loanaccount.application;

import com.mfin.loan.engine.Money;
import com.mfin.loanaccount.domain.AllocationOrder;
import com.mfin.loanaccount.domain.ScheduleInstallment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Apportions a repayment across a loan's installments.
 *
 * <p>The rules, in order:</p>
 * <ol>
 *   <li><b>Oldest debt first.</b> Installments already due are settled before any future one, so
 *       a borrower in arrears clears the arrears rather than paying ahead.</li>
 *   <li><b>Bucket order within an installment</b> follows the configured
 *       {@link AllocationOrder} - penalties, fees, interest, principal by default.</li>
 *   <li><b>Surplus becomes advance payment.</b> Money left after every due installment is
 *       applied to future installments in date order; anything still left is held as an advance
 *       balance rather than refunded automatically.</li>
 * </ol>
 *
 * <p>This class is pure: it reads the installments and produces a plan, but changes nothing.
 * That is what makes it exhaustively unit-testable and lets the caller decide whether the plan
 * is being previewed (a payoff quote) or applied.</p>
 */
@Component
public class RepaymentAllocator {

    /**
     * Builds the allocation plan for {@code amount} against the supplied installments.
     *
     * @param installments the loan's full schedule; order is irrelevant, it is sorted here
     * @param amount       the payment, which must be positive
     * @param valueDate    the date the payment is treated as received on - this decides which
     *                     installments count as "due"
     * @param order        the institution's allocation policy
     */
    public AllocationPlan allocate(List<ScheduleInstallment> installments, BigDecimal amount,
                                   LocalDate valueDate, AllocationOrder order) {
        List<ScheduleInstallment> sorted = installments.stream()
                .sorted(Comparator.comparing(ScheduleInstallment::getDueDate)
                        .thenComparing(ScheduleInstallment::getInstallmentNumber))
                .toList();

        BigDecimal remaining = amount;
        List<InstallmentAllocation> allocations = new ArrayList<>();

        // Pass 1: everything already due, oldest first.
        for (ScheduleInstallment installment : sorted) {
            if (!Money.isPositive(remaining)) {
                break;
            }
            if (!installment.isDueBy(valueDate) || installment.isSettled()) {
                continue;
            }
            InstallmentAllocation allocation = allocateTo(installment, remaining, order);
            if (allocation.hasAmount()) {
                allocations.add(allocation);
                remaining = remaining.subtract(allocation.total());
            }
        }

        // Pass 2: pay ahead into future installments.
        BigDecimal advanceApplied = BigDecimal.ZERO;
        for (ScheduleInstallment installment : sorted) {
            if (!Money.isPositive(remaining)) {
                break;
            }
            if (installment.isDueBy(valueDate) || installment.isSettled()) {
                continue;
            }
            InstallmentAllocation allocation = allocateTo(installment, remaining, order);
            if (allocation.hasAmount()) {
                allocations.add(allocation.asAdvance());
                remaining = remaining.subtract(allocation.total());
                advanceApplied = advanceApplied.add(allocation.total());
            }
        }

        // Anything beyond the entire outstanding contract is a genuine overpayment. It is
        // carried as a credit rather than silently kept or refunded here.
        //
        // Note that advanceApplied is *reporting only*: paying a future installment early still
        // settles that installment's buckets, so it is already counted in the principal/interest
        // totals below. Only `excess` - money that reached no installment at all - becomes a
        // credit balance, which is why the two must never be added together.
        BigDecimal excess = remaining;

        return new AllocationPlan(allocations, totalOf(allocations, Bucket.PRINCIPAL),
                totalOf(allocations, Bucket.INTEREST), totalOf(allocations, Bucket.FEE),
                totalOf(allocations, Bucket.PENALTY), advanceApplied, excess);
    }

    /** Applies as much of {@code available} as this installment can absorb, bucket by bucket. */
    private InstallmentAllocation allocateTo(ScheduleInstallment installment, BigDecimal available,
                                             AllocationOrder order) {
        BigDecimal remaining = available;
        BigDecimal principal = BigDecimal.ZERO;
        BigDecimal interest = BigDecimal.ZERO;
        BigDecimal fee = BigDecimal.ZERO;
        BigDecimal penalty = BigDecimal.ZERO;

        for (AllocationOrder.Bucket bucket : order.buckets()) {
            if (!Money.isPositive(remaining)) {
                break;
            }
            BigDecimal outstanding = switch (bucket) {
                case PENALTY -> installment.outstandingPenalty();
                case FEE -> installment.outstandingFee();
                case INTEREST -> installment.outstandingInterest();
                case PRINCIPAL -> installment.outstandingPrincipal();
            };
            if (!Money.isPositive(outstanding)) {
                continue;
            }
            BigDecimal applied = Money.min(remaining, outstanding);
            remaining = remaining.subtract(applied);
            switch (bucket) {
                case PENALTY -> penalty = applied;
                case FEE -> fee = applied;
                case INTEREST -> interest = applied;
                case PRINCIPAL -> principal = applied;
            }
        }
        return new InstallmentAllocation(installment.getId(), installment.getInstallmentNumber(),
                installment.getDueDate(), principal, interest, fee, penalty, false);
    }

    private BigDecimal totalOf(List<InstallmentAllocation> allocations, Bucket bucket) {
        return allocations.stream()
                .map(allocation -> switch (bucket) {
                    case PRINCIPAL -> allocation.principal();
                    case INTEREST -> allocation.interest();
                    case FEE -> allocation.fee();
                    case PENALTY -> allocation.penalty();
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private enum Bucket {
        PRINCIPAL, INTEREST, FEE, PENALTY
    }

    /** What one installment received from a payment. */
    public record InstallmentAllocation(
            java.util.UUID installmentId,
            int installmentNumber,
            LocalDate dueDate,
            BigDecimal principal,
            BigDecimal interest,
            BigDecimal fee,
            BigDecimal penalty,
            boolean advance
    ) {
        public BigDecimal total() {
            return principal.add(interest).add(fee).add(penalty);
        }

        public boolean hasAmount() {
            return Money.isPositive(total());
        }

        public InstallmentAllocation asAdvance() {
            return new InstallmentAllocation(installmentId, installmentNumber, dueDate,
                    principal, interest, fee, penalty, true);
        }
    }

    /**
     * The complete plan for one payment.
     *
     * @param principal      principal settled across every installment touched
     * @param advanceApplied how much of the above went to installments not yet due; reporting
     *                       only, already included in the bucket totals
     * @param excess         money that exceeded the entire outstanding contract and became a credit
     */
    public record AllocationPlan(
            List<InstallmentAllocation> allocations,
            BigDecimal principal,
            BigDecimal interest,
            BigDecimal fee,
            BigDecimal penalty,
            BigDecimal advanceApplied,
            BigDecimal excess
    ) {
        /** Total actually applied to installments, excluding any unapplied overpayment. */
        public BigDecimal applied() {
            return principal.add(interest).add(fee).add(penalty);
        }

        /** Everything the payment accounted for; must equal the payment amount. */
        public BigDecimal total() {
            return applied().add(excess);
        }
    }
}

package com.mfin.common.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Integration events for the lending lifecycle. These are the messages that drive the
 * disbursement saga (origination -> loan account -> ledger -> notification) and the
 * repayment saga (payment -> loan account -> ledger -> notification).
 */
public final class LoanEvents {

    private LoanEvents() {
    }

    /** Emitted by loan origination once an application is fully approved. */
    public record LoanApproved(
            UUID eventId,
            UUID tenantId,
            UUID applicationId,
            UUID customerId,
            UUID productId,
            BigDecimal approvedAmount,
            BigDecimal annualInterestRate,
            int numberOfInstallments,
            String repaymentFrequency,
            String interestMethod,
            UUID approvedBy,
            Instant occurredAt
    ) implements DomainEvent {
        public static final String TYPE = "loan.approved.v1";

        @Override
        public String eventType() {
            return TYPE;
        }

        @Override
        public String topic() {
            return Topics.LOAN_APPLICATION;
        }

        @Override
        public String aggregateType() {
            return "LoanApplication";
        }

        @Override
        public UUID aggregateId() {
            return applicationId;
        }
    }

    /**
     * Emitted by loan origination when funds are released. The loan account service consumes
     * this to open the account and materialise the repayment schedule.
     */
    public record LoanDisbursed(
            UUID eventId,
            UUID tenantId,
            UUID applicationId,
            UUID customerId,
            UUID productId,
            BigDecimal principal,
            BigDecimal netDisbursedAmount,
            LocalDate disbursementDate,
            LocalDate firstRepaymentDate,
            String disbursementMethod,
            String referenceNumber,
            Instant occurredAt
    ) implements DomainEvent {
        public static final String TYPE = "loan.disbursed.v1";

        @Override
        public String eventType() {
            return TYPE;
        }

        @Override
        public String topic() {
            return Topics.LOAN_APPLICATION;
        }

        @Override
        public String aggregateType() {
            return "LoanApplication";
        }

        @Override
        public UUID aggregateId() {
            return applicationId;
        }
    }

    /** Emitted once the loan account and schedule exist; closes the disbursement saga. */
    public record LoanAccountOpened(
            UUID eventId,
            UUID tenantId,
            UUID loanAccountId,
            String accountNumber,
            UUID applicationId,
            UUID customerId,
            BigDecimal principal,
            BigDecimal totalRepayable,
            LocalDate maturityDate,
            Instant occurredAt
    ) implements DomainEvent {
        public static final String TYPE = "loan.account-opened.v1";

        @Override
        public String eventType() {
            return TYPE;
        }

        @Override
        public String topic() {
            return Topics.LOAN_ACCOUNT;
        }

        @Override
        public String aggregateType() {
            return "LoanAccount";
        }

        @Override
        public UUID aggregateId() {
            return loanAccountId;
        }
    }

    /** Emitted when the loan account service could not open an account for a disbursed loan. */
    public record LoanAccountOpeningFailed(
            UUID eventId,
            UUID tenantId,
            UUID applicationId,
            String reason,
            Instant occurredAt
    ) implements DomainEvent {
        public static final String TYPE = "loan.account-opening-failed.v1";

        @Override
        public String eventType() {
            return TYPE;
        }

        @Override
        public String topic() {
            return Topics.LOAN_ACCOUNT;
        }

        @Override
        public String aggregateType() {
            return "LoanApplication";
        }

        @Override
        public UUID aggregateId() {
            return applicationId;
        }
    }

    /** Emitted when a loan's outstanding balance reaches zero. */
    public record LoanClosed(
            UUID eventId,
            UUID tenantId,
            UUID loanAccountId,
            UUID customerId,
            LocalDate closedOn,
            Instant occurredAt
    ) implements DomainEvent {
        public static final String TYPE = "loan.closed.v1";

        @Override
        public String eventType() {
            return TYPE;
        }

        @Override
        public String topic() {
            return Topics.LOAN_ACCOUNT;
        }

        @Override
        public String aggregateType() {
            return "LoanAccount";
        }

        @Override
        public UUID aggregateId() {
            return loanAccountId;
        }
    }

    /** Emitted when an installment passes its due date unpaid. Drives dunning and provisioning. */
    public record LoanOverdue(
            UUID eventId,
            UUID tenantId,
            UUID loanAccountId,
            UUID customerId,
            int daysPastDue,
            BigDecimal overdueAmount,
            Instant occurredAt
    ) implements DomainEvent {
        public static final String TYPE = "loan.overdue.v1";

        @Override
        public String eventType() {
            return TYPE;
        }

        @Override
        public String topic() {
            return Topics.LOAN_ACCOUNT;
        }

        @Override
        public String aggregateType() {
            return "LoanAccount";
        }

        @Override
        public UUID aggregateId() {
            return loanAccountId;
        }
    }
}

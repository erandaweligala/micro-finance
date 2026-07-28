package com.mfin.origination.application;

import com.mfin.common.error.ApiExceptions;
import com.mfin.common.events.LoanEvents;
import com.mfin.common.outbox.DomainEventPublisher;
import com.mfin.common.tenant.TenantContext;
import com.mfin.common.web.PageResponse;
import com.mfin.loan.engine.LoanCalculationRequest;
import com.mfin.loan.engine.LoanCalculator;
import com.mfin.loan.engine.LoanSchedule;
import com.mfin.origination.client.CustomerClient;
import com.mfin.origination.client.ProductClient;
import com.mfin.origination.domain.LoanApplication;
import com.mfin.origination.domain.LoanApplicationStatus;
import com.mfin.origination.repository.LoanApplicationRepository;
import com.mfin.origination.web.dto.ApplicationDtos.ApplicationResponse;
import com.mfin.origination.web.dto.ApplicationDtos.ApplicationSummary;
import com.mfin.origination.web.dto.ApplicationDtos.ApproveRequest;
import com.mfin.origination.web.dto.ApplicationDtos.CreateApplicationRequest;
import com.mfin.origination.web.dto.ApplicationDtos.DisburseRequest;
import com.mfin.origination.web.dto.ApplicationDtos.UpdateApplicationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The loan origination workflow.
 *
 * <p>Disbursement is the point where this service stops owning the loan: it records the release
 * of funds and publishes {@code loan.disbursed.v1} <em>in the same transaction</em> through the
 * outbox. The loan account service then opens the account and materialises the schedule. That
 * is the first leg of the disbursement saga; if account opening fails, the compensating
 * {@code loan.account-opening-failed.v1} event brings the application back for operator
 * attention rather than leaving money released against no loan.</p>
 */
@Service
public class LoanApplicationService {

    private static final Logger log = LoggerFactory.getLogger(LoanApplicationService.class);

    private final LoanApplicationRepository repository;
    private final ProductClient productClient;
    private final CustomerClient customerClient;
    private final LoanCalculator calculator;
    private final DomainEventPublisher eventPublisher;

    public LoanApplicationService(LoanApplicationRepository repository,
                                  ProductClient productClient,
                                  CustomerClient customerClient,
                                  LoanCalculator calculator,
                                  DomainEventPublisher eventPublisher) {
        this.repository = repository;
        this.productClient = productClient;
        this.customerClient = customerClient;
        this.calculator = calculator;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ApplicationResponse create(CreateApplicationRequest request, String bearerToken) {
        UUID tenantId = TenantContext.requireTenantId();
        var customer = customerClient.fetchEligibility(request.customerId(), bearerToken);
        if (!customer.eligibleForLending()) {
            throw new ApiExceptions.BusinessRuleException(
                    "This customer is not eligible to borrow (status " + customer.status()
                            + ", KYC " + customer.kycStatus() + ")");
        }
        var product = productClient.fetch(request.productId(), bearerToken);
        if (!product.isActive()) {
            throw new ApiExceptions.BusinessRuleException(
                    "Loan product '" + product.name() + "' is not available for new applications");
        }

        BigDecimal rate = request.annualInterestRate() != null
                ? request.annualInterestRate() : product.defaultAnnualRate();

        LoanApplication application = new LoanApplication(nextApplicationNumber(tenantId),
                request.customerId(), request.productId(), product.currency(),
                request.requestedAmount(), request.requestedInstallments());
        application.setTerms(rate,
                request.interestMethod() != null ? request.interestMethod() : product.interestMethod(),
                request.repaymentFrequency() != null
                        ? request.repaymentFrequency() : product.repaymentFrequency(),
                request.graceType() != null ? request.graceType() : product.graceType(),
                request.gracePeriods());
        application.setRequested(request.requestedAmount(), request.requestedInstallments(),
                request.purpose(), request.expectedDisbursementDate(), request.firstRepaymentDate());
        application.describe(customer.fullName(), product.name(), customer.branchId(),
                TenantContext.require().userId(), product.approvalLevels());

        validateAgainstProduct(application, product);
        return ApplicationResponse.from(repository.save(application));
    }

    @Transactional
    public ApplicationResponse update(UUID applicationId, UpdateApplicationRequest request,
                                      String bearerToken) {
        LoanApplication application = require(applicationId);
        application.requireEditable();

        var product = productClient.fetch(application.getProductId(), bearerToken);
        application.setRequested(request.requestedAmount(), request.requestedInstallments(),
                request.purpose(), request.expectedDisbursementDate(), request.firstRepaymentDate());
        application.setTerms(
                request.annualInterestRate() != null
                        ? request.annualInterestRate() : product.defaultAnnualRate(),
                request.interestMethod() != null ? request.interestMethod() : product.interestMethod(),
                request.repaymentFrequency() != null
                        ? request.repaymentFrequency() : product.repaymentFrequency(),
                request.graceType(), request.gracePeriods());

        validateAgainstProduct(application, product);
        return ApplicationResponse.from(repository.save(application));
    }

    @Transactional
    public ApplicationResponse submit(UUID applicationId) {
        LoanApplication application = require(applicationId);
        application.submit();
        return ApplicationResponse.from(repository.save(application));
    }

    @Transactional
    public ApplicationResponse beginReview(UUID applicationId, String comment) {
        LoanApplication application = require(applicationId);
        application.beginReview(TenantContext.require().userId(), comment);
        return ApplicationResponse.from(repository.save(application));
    }

    @Transactional
    public ApplicationResponse approve(UUID applicationId, ApproveRequest request,
                                       String bearerToken) {
        LoanApplication application = require(applicationId);

        if (request.approvedAmount() != null
                && request.approvedAmount().compareTo(application.getRequestedAmount()) > 0) {
            // Approving more than was asked for would bypass the applicant's own request.
            throw new ApiExceptions.BusinessRuleException(
                    "The approved amount cannot exceed the requested amount");
        }

        boolean fullyApproved = application.approve(TenantContext.require().userId(),
                request.comment(), request.approvedAmount(), request.approvedInstallments());

        if (fullyApproved) {
            // Re-validate: an approver may have altered the terms.
            var product = productClient.fetch(application.getProductId(), bearerToken);
            validateAgainstProduct(application, product);

            eventPublisher.publish(new LoanEvents.LoanApproved(UUID.randomUUID(),
                    application.getTenantId(), application.getId(), application.getCustomerId(),
                    application.getProductId(), application.effectiveAmount(),
                    application.getAnnualInterestRate(), application.effectiveInstallments(),
                    application.getRepaymentFrequency().name(),
                    application.getInterestMethod().name(),
                    TenantContext.require().userId(), Instant.now()));
        }
        return ApplicationResponse.from(repository.save(application));
    }

    @Transactional
    public ApplicationResponse reject(UUID applicationId, String reason) {
        LoanApplication application = require(applicationId);
        application.reject(TenantContext.require().userId(), reason);
        return ApplicationResponse.from(repository.save(application));
    }

    @Transactional
    public ApplicationResponse cancel(UUID applicationId, String reason) {
        LoanApplication application = require(applicationId);
        application.cancel(TenantContext.require().userId(), reason);
        return ApplicationResponse.from(repository.save(application));
    }

    /**
     * Records disbursement and hands the loan over to the loan account service.
     *
     * <p>The net amount is recomputed here from the product's fee configuration rather than
     * taken from the request, so a client cannot understate what was deducted.</p>
     */
    @Transactional
    public ApplicationResponse disburse(UUID applicationId, DisburseRequest request,
                                        String bearerToken) {
        LoanApplication application = require(applicationId);
        if (request.disbursementDate().isAfter(LocalDate.now())) {
            throw new ApiExceptions.BusinessRuleException(
                    "Funds cannot be recorded as disbursed on a future date");
        }

        var product = productClient.fetch(application.getProductId(), bearerToken);
        LoanSchedule schedule = calculator.generate(buildCalculationRequest(application, product,
                request.disbursementDate()));

        application.markDisbursed(request.method(), request.reference(), request.disbursementDate(),
                request.amount(), schedule.netDisbursedAmount());

        eventPublisher.publish(new LoanEvents.LoanDisbursed(UUID.randomUUID(),
                application.getTenantId(), application.getId(), application.getCustomerId(),
                application.getProductId(), application.effectiveAmount(),
                schedule.netDisbursedAmount(), request.disbursementDate(),
                schedule.effectiveFirstDueDate(), request.method().name(), request.reference(),
                Instant.now()));

        log.info("Application {} disbursed: {} {} via {} ref {}", application.getApplicationNumber(),
                application.getCurrency(), application.effectiveAmount(), request.method(),
                request.reference());
        return ApplicationResponse.from(repository.save(application));
    }

    @Transactional(readOnly = true)
    public PageResponse<ApplicationSummary> search(LoanApplicationStatus status, UUID customerId,
                                                   UUID branchId, String query, Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();
        String normalised = query == null || query.isBlank() ? null : query.trim();
        return PageResponse.from(repository.search(tenantId, status, customerId, branchId,
                normalised, pageable), ApplicationSummary::from);
    }

    @Transactional(readOnly = true)
    public ApplicationResponse get(UUID applicationId) {
        return ApplicationResponse.from(require(applicationId));
    }

    /** The schedule the borrower would receive on these terms; recomputed, never stored here. */
    @Transactional(readOnly = true)
    public LoanSchedule previewSchedule(UUID applicationId, String bearerToken) {
        LoanApplication application = require(applicationId);
        var product = productClient.fetch(application.getProductId(), bearerToken);
        LocalDate start = application.getDisbursementDate() != null
                ? application.getDisbursementDate()
                : application.getExpectedDisbursementDate() != null
                        ? application.getExpectedDisbursementDate() : LocalDate.now();
        return calculator.generate(buildCalculationRequest(application, product, start));
    }

    private LoanCalculationRequest buildCalculationRequest(LoanApplication application,
                                                           ProductClient.ProductTerms product,
                                                           LocalDate disbursementDate) {
        return LoanCalculationRequest.builder()
                .principal(application.effectiveAmount())
                .annualInterestRate(application.getAnnualInterestRate())
                .numberOfInstallments(application.effectiveInstallments())
                .frequency(application.getRepaymentFrequency())
                .interestMethod(application.getInterestMethod())
                .disbursementDate(disbursementDate)
                .firstRepaymentDate(application.getFirstRepaymentDate())
                .graceType(application.getGraceType())
                .gracePeriods(application.getGracePeriods())
                .feeType(product.feeType())
                .feeValue(product.feeValue())
                .feeCollection(product.feeCollection())
                .dayCount(product.dayCount())
                .currencyScale(product.currencyScale())
                .build();
    }

    /**
     * Enforces product policy locally. The product service owns the bounds; origination applies
     * them, because it is the service that knows the negotiated terms.
     */
    private void validateAgainstProduct(LoanApplication application,
                                        ProductClient.ProductTerms product) {
        BigDecimal amount = application.effectiveAmount();
        java.util.List<String> problems = new java.util.ArrayList<>();
        if (amount.compareTo(product.minPrincipal()) < 0 || amount.compareTo(product.maxPrincipal()) > 0) {
            problems.add("amount must be between " + product.minPrincipal()
                    + " and " + product.maxPrincipal());
        }
        BigDecimal rate = application.getAnnualInterestRate();
        if (rate.compareTo(product.minAnnualRate()) < 0 || rate.compareTo(product.maxAnnualRate()) > 0) {
            problems.add("interest rate must be between " + product.minAnnualRate()
                    + "% and " + product.maxAnnualRate() + "%");
        }
        int installments = application.effectiveInstallments();
        if (installments < product.minInstallments() || installments > product.maxInstallments()) {
            problems.add("installments must be between " + product.minInstallments()
                    + " and " + product.maxInstallments());
        }
        if (application.getGracePeriods() > product.maxGracePeriods()) {
            problems.add("grace periods must not exceed " + product.maxGracePeriods());
        }
        if (!problems.isEmpty()) {
            throw new ApiExceptions.BusinessRuleException(
                    "These terms breach the product's policy: " + String.join("; ", problems));
        }
    }

    private LoanApplication require(UUID applicationId) {
        UUID tenantId = TenantContext.requireTenantId();
        return repository.findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException(
                        "Loan application", applicationId));
    }

    private String nextApplicationNumber(UUID tenantId) {
        return String.format("APP-%06d", repository.maxApplicationSequence(tenantId) + 1);
    }
}

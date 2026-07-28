package com.mfin.payment.application;

import com.mfin.common.error.ApiExceptions;
import com.mfin.common.events.PaymentEvents;
import com.mfin.common.idempotency.IdempotencyService;
import com.mfin.common.outbox.DomainEventPublisher;
import com.mfin.common.tenant.TenantContext;
import com.mfin.common.web.PageResponse;
import com.mfin.payment.client.LoanAccountClient;
import com.mfin.payment.domain.Payment;
import com.mfin.payment.domain.PaymentStatus;
import com.mfin.payment.repository.PaymentRepository;
import com.mfin.payment.web.dto.PaymentDtos.CapturePaymentRequest;
import com.mfin.payment.web.dto.PaymentDtos.PaymentResponse;
import com.mfin.payment.web.dto.PaymentDtos.ReceiptResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Receiving money.
 *
 * <p>The division of responsibility is deliberate: this service owns the <em>receipt</em> -
 * the channel, the reference, the cashier, the idempotency guarantee - while the loan account
 * service owns the <em>balances</em> and performs the allocation. Neither duplicates the other,
 * and the loan book has exactly one writer.</p>
 *
 * <p>Ordering within {@link #capture} matters. The loan is updated first and the receipt is
 * written afterwards, because the loan account call is idempotent and can therefore be safely
 * repeated, whereas a receipt written before a failed posting would promise the customer
 * something that never reached their loan.</p>
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String OPERATION = "payment.capture";

    private final PaymentRepository repository;
    private final LoanAccountClient loanAccountClient;
    private final IdempotencyService idempotencyService;
    private final DomainEventPublisher eventPublisher;

    public PaymentService(PaymentRepository repository,
                          LoanAccountClient loanAccountClient,
                          IdempotencyService idempotencyService,
                          DomainEventPublisher eventPublisher) {
        this.repository = repository;
        this.loanAccountClient = loanAccountClient;
        this.idempotencyService = idempotencyService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Records a repayment exactly once.
     *
     * <p>The {@code Idempotency-Key} makes the whole operation replay-safe at the edge: a
     * retried request returns the original receipt without touching the loan again. The loan
     * account service's own idempotency covers the narrower window where this service posted
     * successfully but crashed before committing the receipt.</p>
     */
    public PaymentResponse capture(String idempotencyKey, CapturePaymentRequest request,
                                   String bearerToken) {
        return idempotencyService.execute(idempotencyKey, OPERATION, request, PaymentResponse.class,
                () -> doCapture(request, bearerToken));
    }

    /**
     * Runs inside the transaction opened by {@link IdempotencyService#execute}, so the receipt,
     * the idempotency record and the outbox event all commit together or not at all. It is
     * deliberately not annotated: a self-invoked {@code @Transactional} method would not be
     * proxied, and implying a transaction boundary that does not exist is worse than none.
     */
    private PaymentResponse doCapture(CapturePaymentRequest request, String bearerToken) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID paymentId = UUID.randomUUID();

        if (request.valueDate().isAfter(LocalDate.now())) {
            throw new ApiExceptions.BusinessRuleException("A payment cannot be dated in the future");
        }

        // Apply to the loan first: if this fails, no receipt is issued at all.
        var result = loanAccountClient.applyRepayment(request.loanAccountId(),
                new LoanAccountClient.ApplyRepaymentRequest(paymentId, request.amount(),
                        request.valueDate(), null),
                bearerToken);
        if (result == null) {
            throw new ApiExceptions.UpstreamUnavailableException("The loan account service");
        }

        // Customer and currency come from the loan account service, which owns them - not from
        // the request, where a client could attribute the payment to the wrong borrower.
        Payment payment = new Payment(nextReceiptNumber(tenantId), request.loanAccountId(),
                result.customerId(), request.amount(), result.currency(), request.method(),
                request.valueDate());
        payment.setId(paymentId);
        payment.describe(request.externalReference(), request.narrative(),
                TenantContext.require().userId(), TenantContext.require().branchId());
        payment.recordAllocation(result.principalAllocated(), result.interestAllocated(),
                result.feeAllocated(), result.penaltyAllocated(), result.excessAmount(),
                result.outstandingPrincipalAfter(), result.totalOutstandingAfter(),
                result.accountNumber());

        Payment saved = repository.save(payment);

        eventPublisher.publish(new PaymentEvents.PaymentPosted(UUID.randomUUID(), tenantId,
                saved.getId(), saved.getLoanAccountId(), saved.getCustomerId(),
                saved.getReceiptNumber(), saved.getAmount(), saved.getPenaltyAllocated(),
                saved.getInterestAllocated(), saved.getPrincipalAllocated(),
                saved.getExcessAmount(), result.outstandingPrincipalAfter(),
                result.totalOutstandingAfter(), saved.getMethod().name(), saved.getValueDate(),
                Instant.now()));

        log.info("Receipt {} issued for {} on loan {}", saved.getReceiptNumber(), saved.getAmount(),
                result.accountNumber());
        return PaymentResponse.from(saved);
    }

    /**
     * Reverses a payment under authorisation.
     *
     * <p>The loan is unwound first; only then is the receipt marked reversed. If the loan call
     * fails the receipt stays POSTED, which is the honest state - the money is still applied.</p>
     */
    @Transactional
    public PaymentResponse reverse(UUID paymentId, String reason, String bearerToken) {
        UUID tenantId = TenantContext.requireTenantId();
        Payment payment = repository.findByIdAndTenantId(paymentId, tenantId)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("Payment", paymentId));
        if (payment.isReversed()) {
            throw new ApiExceptions.BusinessRuleException(
                    "Receipt " + payment.getReceiptNumber() + " has already been reversed");
        }

        loanAccountClient.reverseRepayment(payment.getLoanAccountId(), paymentId, reason, bearerToken);

        payment.reverse(TenantContext.require().userId(), reason);
        Payment saved = repository.save(payment);

        eventPublisher.publish(new PaymentEvents.PaymentReversed(UUID.randomUUID(), tenantId,
                UUID.randomUUID(), saved.getLoanAccountId(), saved.getId(), saved.getAmount(),
                reason, TenantContext.require().userId(), Instant.now()));

        log.warn("Receipt {} reversed by {}: {}", saved.getReceiptNumber(),
                TenantContext.require().userId(), reason);
        return PaymentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> search(UUID loanAccountId, UUID customerId,
                                                PaymentStatus status, LocalDate from, LocalDate to,
                                                String query, Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();
        String normalised = query == null || query.isBlank() ? null : query.trim();
        return PageResponse.from(repository.search(tenantId, loanAccountId, customerId, status,
                from, to, normalised, pageable), PaymentResponse::from);
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(UUID paymentId) {
        return PaymentResponse.from(require(paymentId));
    }

    @Transactional(readOnly = true)
    public ReceiptResponse receipt(UUID paymentId) {
        Payment payment = require(paymentId);
        // The institution name and cashier name are resolved by the caller's own services in a
        // fuller implementation; the receipt is complete without them.
        return ReceiptResponse.from(payment, null, null);
    }

    private Payment require(UUID paymentId) {
        UUID tenantId = TenantContext.requireTenantId();
        return repository.findByIdAndTenantId(paymentId, tenantId)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("Payment", paymentId));
    }

    private String nextReceiptNumber(UUID tenantId) {
        return String.format("RCP-%08d", repository.maxReceiptSequence(tenantId) + 1);
    }
}

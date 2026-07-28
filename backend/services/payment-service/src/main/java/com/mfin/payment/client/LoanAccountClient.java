package com.mfin.payment.client;

import com.mfin.common.error.ApiExceptions;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Posts repayments to the loan account service, which owns the balances.
 *
 * <p>The call is safe to retry: the loan account service is idempotent on {@code paymentId},
 * so a timeout followed by a retry replays the original allocation rather than posting twice.
 * That is precisely why the payment id is generated here, before the call, instead of being
 * assigned by the callee.</p>
 */
@Component
public class LoanAccountClient {

    private static final Logger log = LoggerFactory.getLogger(LoanAccountClient.class);
    private static final String CIRCUIT = "loan-account-service";

    private final RestClient restClient;

    public LoanAccountClient(RestClient.Builder builder,
                             @Value("${mfin.clients.loan-account.base-url:http://loan-account-service:8086}")
                             String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Retry(name = CIRCUIT)
    @CircuitBreaker(name = CIRCUIT, fallbackMethod = "unavailable")
    public RepaymentResult applyRepayment(UUID loanAccountId, ApplyRepaymentRequest request,
                                          String bearerToken) {
        return restClient.post()
                .uri("/internal/v1/loan-accounts/{id}/repayments", loanAccountId)
                .header("Authorization", bearerToken)
                .body(request)
                .retrieve()
                .body(RepaymentResult.class);
    }

    @Retry(name = CIRCUIT)
    @CircuitBreaker(name = CIRCUIT, fallbackMethod = "reversalUnavailable")
    public RepaymentResult reverseRepayment(UUID loanAccountId, UUID paymentId, String reason,
                                            String bearerToken) {
        return restClient.post()
                .uri("/internal/v1/loan-accounts/{id}/repayments/{paymentId}/reverse",
                        loanAccountId, paymentId)
                .header("Authorization", bearerToken)
                .body(new ReverseRepaymentRequest(reason))
                .retrieve()
                .body(RepaymentResult.class);
    }

    @SuppressWarnings("unused")
    private RepaymentResult unavailable(UUID loanAccountId, ApplyRepaymentRequest request,
                                        String bearerToken, Throwable cause) {
        // Refusing the payment is the safe failure: the cashier retries, and no receipt exists
        // for money that was never applied to a loan.
        log.error("Loan account service unavailable while posting payment {} to loan {}",
                request.paymentId(), loanAccountId, cause);
        throw new ApiExceptions.UpstreamUnavailableException("The loan account service");
    }

    @SuppressWarnings("unused")
    private RepaymentResult reversalUnavailable(UUID loanAccountId, UUID paymentId, String reason,
                                                String bearerToken, Throwable cause) {
        log.error("Loan account service unavailable while reversing payment {}", paymentId, cause);
        throw new ApiExceptions.UpstreamUnavailableException("The loan account service");
    }

    public record ApplyRepaymentRequest(UUID paymentId, BigDecimal amount, LocalDate valueDate,
                                        String allocationOrder) {
    }

    public record ReverseRepaymentRequest(String reason) {
    }

    public record RepaymentResult(
            UUID paymentId,
            UUID loanAccountId,
            String accountNumber,
            /* Authoritative: the loan account service owns which borrower a loan belongs to. */
            UUID customerId,
            String currency,
            BigDecimal amount,
            BigDecimal penaltyAllocated,
            BigDecimal interestAllocated,
            BigDecimal feeAllocated,
            BigDecimal principalAllocated,
            BigDecimal excessAmount,
            BigDecimal outstandingPrincipalAfter,
            BigDecimal totalOutstandingAfter,
            String status,
            boolean reversed
    ) {
    }
}

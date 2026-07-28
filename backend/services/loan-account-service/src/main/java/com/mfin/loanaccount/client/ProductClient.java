package com.mfin.loanaccount.client;

import com.mfin.common.error.ApiExceptions;
import com.mfin.loan.engine.DayCountConvention;
import com.mfin.loan.engine.FeeCollection;
import com.mfin.loan.engine.FeeType;
import com.mfin.loan.engine.InterestMethod;
import com.mfin.loan.engine.RepaymentFrequency;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Reads product terms when opening a loan account.
 *
 * <p>This call happens on the event-consumer thread, where there is no inbound bearer token to
 * forward, so it authenticates with the service's own client credentials and passes the tenant
 * explicitly. That token grants only the SYSTEM role.
 */
@Component
public class ProductClient {

    private static final Logger log = LoggerFactory.getLogger(ProductClient.class);
    private static final String CIRCUIT = "loan-product-service";

    private final RestClient restClient;
    private final ServiceTokenProvider tokenProvider;

    public ProductClient(RestClient.Builder builder,
                         ServiceTokenProvider tokenProvider,
                         @Value("${mfin.clients.loan-product.base-url:http://loan-product-service:8084}")
                         String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.tokenProvider = tokenProvider;
    }

    @Retry(name = CIRCUIT)
    @CircuitBreaker(name = CIRCUIT, fallbackMethod = "unavailable")
    public ProductTerms fetchInternal(UUID tenantId, UUID productId) {
        return restClient.get()
                .uri("/api/v1/loan-products/{id}", productId)
                .header("Authorization", tokenProvider.bearerToken(tenantId))
                .header("X-Tenant-Id", tenantId.toString())
                .retrieve()
                .body(ProductTerms.class);
    }

    @SuppressWarnings("unused")
    private ProductTerms unavailable(UUID tenantId, UUID productId, Throwable cause) {
        // Failing here leaves the disbursement event unacknowledged, so it is retried rather
        // than silently dropped: a disbursed loan with no account is not an acceptable outcome.
        log.error("Loan product service unavailable while opening an account for product {}",
                productId, cause);
        throw new ApiExceptions.UpstreamUnavailableException("The loan product service");
    }

    public record ProductTerms(
            UUID id,
            String code,
            String name,
            String currency,
            int currencyScale,
            BigDecimal defaultAnnualRate,
            InterestMethod interestMethod,
            RepaymentFrequency repaymentFrequency,
            int defaultInstallments,
            FeeType feeType,
            BigDecimal feeValue,
            FeeCollection feeCollection,
            DayCountConvention dayCount,
            BigDecimal penaltyAnnualRate,
            String penaltyBasis,
            int penaltyGraceDays,
            int daysToDefault,
            String status
    ) {
    }
}

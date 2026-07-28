package com.mfin.origination.client;

import com.mfin.common.error.ApiExceptions;
import com.mfin.loan.engine.DayCountConvention;
import com.mfin.loan.engine.FeeCollection;
import com.mfin.loan.engine.FeeType;
import com.mfin.loan.engine.GraceType;
import com.mfin.loan.engine.InterestMethod;
import com.mfin.loan.engine.RepaymentFrequency;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Reads product terms from the loan product service.
 *
 * <p>Services never share a database, so this is a plain HTTP call - wrapped in a retry and a
 * circuit breaker, because origination must degrade predictably when the product service is
 * slow rather than piling up threads waiting on it.</p>
 */
@Component
public class ProductClient {

    private static final Logger log = LoggerFactory.getLogger(ProductClient.class);
    private static final String CIRCUIT = "loan-product-service";

    private final RestClient restClient;

    public ProductClient(RestClient.Builder builder,
                         @org.springframework.beans.factory.annotation.Value(
                                 "${mfin.clients.loan-product.base-url:http://loan-product-service:8084}")
                         String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    /**
     * Fetches the product a loan is being written against.
     *
     * <p>The caller's bearer token is forwarded so the product service applies the same tenant
     * scoping - this service never holds a privileged credential that could read another
     * institution's products.</p>
     */
    @Retry(name = CIRCUIT)
    @CircuitBreaker(name = CIRCUIT, fallbackMethod = "unavailable")
    public ProductTerms fetch(UUID productId, String bearerToken) {
        return restClient.get()
                .uri("/api/v1/loan-products/{id}", productId)
                .header("Authorization", bearerToken)
                .retrieve()
                .body(ProductTerms.class);
    }

    @SuppressWarnings("unused") // resolved by Resilience4j
    private ProductTerms unavailable(UUID productId, String bearerToken, Throwable cause) {
        log.error("Loan product service unavailable while fetching product {}", productId, cause);
        throw new ApiExceptions.UpstreamUnavailableException("The loan product service");
    }

    /**
     * The subset of the product contract origination depends on. Deliberately narrow: a field
     * this service does not read is a field the product service can change freely.
     */
    public record ProductTerms(
            UUID id,
            String code,
            String name,
            String currency,
            int currencyScale,
            BigDecimal minPrincipal,
            BigDecimal maxPrincipal,
            BigDecimal minAnnualRate,
            BigDecimal maxAnnualRate,
            BigDecimal defaultAnnualRate,
            InterestMethod interestMethod,
            RepaymentFrequency repaymentFrequency,
            int minInstallments,
            int maxInstallments,
            int defaultInstallments,
            GraceType graceType,
            int maxGracePeriods,
            FeeType feeType,
            BigDecimal feeValue,
            FeeCollection feeCollection,
            DayCountConvention dayCount,
            int approvalLevels,
            String status
    ) {
        public boolean isActive() {
            return "ACTIVE".equals(status);
        }
    }
}

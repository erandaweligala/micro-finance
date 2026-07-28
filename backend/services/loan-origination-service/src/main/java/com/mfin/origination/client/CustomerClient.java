package com.mfin.origination.client;

import com.mfin.common.error.ApiExceptions;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/** Checks borrower eligibility with the customer service before an application is accepted. */
@Component
public class CustomerClient {

    private static final Logger log = LoggerFactory.getLogger(CustomerClient.class);
    private static final String CIRCUIT = "customer-service";

    private final RestClient restClient;

    public CustomerClient(RestClient.Builder builder,
                          @Value("${mfin.clients.customer.base-url:http://customer-service:8083}")
                          String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Retry(name = CIRCUIT)
    @CircuitBreaker(name = CIRCUIT, fallbackMethod = "unavailable")
    public CustomerEligibility fetchEligibility(UUID customerId, String bearerToken) {
        return restClient.get()
                .uri("/api/v1/customers/{id}/eligibility", customerId)
                .header("Authorization", bearerToken)
                .retrieve()
                .body(CustomerEligibility.class);
    }

    @SuppressWarnings("unused")
    private CustomerEligibility unavailable(UUID customerId, String bearerToken, Throwable cause) {
        log.error("Customer service unavailable while checking customer {}", customerId, cause);
        throw new ApiExceptions.UpstreamUnavailableException("The customer service");
    }

    public record CustomerEligibility(
            UUID customerId,
            String fullName,
            boolean eligibleForLending,
            String kycStatus,
            String status,
            UUID branchId
    ) {
    }
}

package com.mfin.tenant.client;

import com.mfin.common.error.ApiExceptions;
import com.mfin.tenant.web.dto.TenantDtos.AdminAccountRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/** Creates the first administrator account for a newly onboarded institution. */
@Component
public class IdentityClient {

    private static final Logger log = LoggerFactory.getLogger(IdentityClient.class);
    private static final String CIRCUIT = "identity-service";

    private final RestClient restClient;

    public IdentityClient(RestClient.Builder builder,
                          @Value("${mfin.clients.identity.base-url:http://identity-service:8081}")
                          String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Retry(name = CIRCUIT)
    @CircuitBreaker(name = CIRCUIT, fallbackMethod = "unavailable")
    public UUID provisionTenantAdmin(UUID tenantId, String slug, String name,
                                     AdminAccountRequest administrator, String bearerToken) {
        ProvisionResponse response = restClient.post()
                .uri("/api/v1/users/provision-tenant-admin")
                .header("Authorization", bearerToken)
                .body(new ProvisionRequest(tenantId, slug, name, administrator.username(),
                        administrator.email(), administrator.fullName(),
                        administrator.temporaryPassword()))
                .retrieve()
                .body(ProvisionResponse.class);
        return response == null ? null : response.id();
    }

    @SuppressWarnings("unused")
    private UUID unavailable(UUID tenantId, String slug, String name,
                             AdminAccountRequest administrator, String bearerToken,
                             Throwable cause) {
        log.error("Identity service unavailable while provisioning the administrator for {}",
                slug, cause);
        throw new ApiExceptions.UpstreamUnavailableException("The identity service");
    }

    private record ProvisionRequest(UUID tenantId, String tenantSlug, String tenantName,
                                    String username, String email, String fullName,
                                    String temporaryPassword) {
    }

    private record ProvisionResponse(UUID id) {
    }
}

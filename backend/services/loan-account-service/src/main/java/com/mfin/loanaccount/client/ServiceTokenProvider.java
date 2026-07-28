package com.mfin.loanaccount.client;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Supplies the service's own access token for background work (event consumers, schedulers)
 * where there is no user request to borrow a token from.
 *
 * <p>In production this performs an OAuth 2.0 client-credentials grant against the identity
 * service and caches the token until shortly before expiry. The client is registered with the
 * SYSTEM role only, so a leaked service token cannot approve a loan or move money.</p>
 */
@Component
public class ServiceTokenProvider {

    private final org.springframework.web.client.RestClient restClient;
    private final String clientId;
    private final String clientSecret;

    private volatile String cachedToken;
    private volatile java.time.Instant expiresAt = java.time.Instant.EPOCH;

    public ServiceTokenProvider(
            org.springframework.web.client.RestClient.Builder builder,
            @org.springframework.beans.factory.annotation.Value(
                    "${mfin.clients.identity.base-url:http://identity-service:8081}") String baseUrl,
            @org.springframework.beans.factory.annotation.Value(
                    "${mfin.clients.identity.client-id:loan-account-service}") String clientId,
            @org.springframework.beans.factory.annotation.Value(
                    "${mfin.clients.identity.client-secret:}") String clientSecret) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /** Returns a bearer token valid for calls made on behalf of {@code tenantId}. */
    public String bearerToken(UUID tenantId) {
        // Refresh a minute early so a token never expires mid-flight.
        if (cachedToken != null && java.time.Instant.now().isBefore(expiresAt.minusSeconds(60))) {
            return "Bearer " + cachedToken;
        }
        synchronized (this) {
            TokenResponse response = restClient.post()
                    .uri("/api/v1/auth/service-token")
                    .body(new ServiceTokenRequest(clientId, clientSecret))
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null) {
                throw new IllegalStateException("Identity service returned no service token");
            }
            this.cachedToken = response.accessToken();
            this.expiresAt = java.time.Instant.now().plusSeconds(response.expiresInSeconds());
            return "Bearer " + cachedToken;
        }
    }

    private record ServiceTokenRequest(String clientId, String clientSecret) {
    }

    private record TokenResponse(String accessToken, long expiresInSeconds) {
    }
}

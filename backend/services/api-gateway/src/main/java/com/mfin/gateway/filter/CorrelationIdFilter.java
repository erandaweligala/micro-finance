package com.mfin.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Stamps every inbound request with a correlation id and strips any client-supplied tenant
 * override that the caller is not entitled to use.
 *
 * <p>The tenant header is only honoured for platform administrators. Rather than trusting the
 * downstream services to re-check it, the gateway removes it outright for everyone else, so a
 * forged {@code X-Tenant-Id} cannot even reach them.</p>
 */
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    private static final String REQUEST_ID = "X-Request-Id";
    private static final String TENANT_OVERRIDE = "X-Tenant-Id";
    private static final int MAX_ID_LENGTH = 64;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestId = sanitise(exchange.getRequest().getHeaders().getFirst(REQUEST_ID));

        return exchange.getPrincipal()
                .cast(org.springframework.security.core.Authentication.class)
                .map(this::isPlatformAdmin)
                .defaultIfEmpty(false)
                .flatMap(platformAdmin -> {
                    ServerWebExchange mutated = exchange.mutate()
                            .request(builder -> {
                                builder.header(REQUEST_ID, requestId);
                                if (!platformAdmin) {
                                    builder.headers(headers -> headers.remove(TENANT_OVERRIDE));
                                }
                            })
                            .build();
                    mutated.getResponse().getHeaders().set(REQUEST_ID, requestId);
                    return chain.filter(mutated);
                });
    }

    private boolean isPlatformAdmin(org.springframework.security.core.Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_PLATFORM_ADMIN".equals(authority.getAuthority()));
    }

    /** Client-supplied ids reach log files, so they are bounded and stripped of control characters. */
    private String sanitise(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String cleaned = candidate.replaceAll("[^A-Za-z0-9._:-]", "");
        if (cleaned.isEmpty()) {
            return UUID.randomUUID().toString();
        }
        return cleaned.length() > MAX_ID_LENGTH ? cleaned.substring(0, MAX_ID_LENGTH) : cleaned;
    }

    @Override
    public int getOrder() {
        // After Spring Security has authenticated, so the principal is available.
        return Ordered.LOWEST_PRECEDENCE - 100;
    }
}

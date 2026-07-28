package com.mfin.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

/**
 * Rate-limit keys.
 *
 * <p>Limits are applied per tenant rather than per IP for authenticated traffic: one
 * institution's runaway integration must not exhaust another's quota, and NAT means many
 * legitimate users share an address. Unauthenticated traffic - notably sign-in - is limited by
 * IP, since there is no tenant yet and that is exactly the endpoint worth protecting from
 * credential stuffing.</p>
 */
@Configuration
public class RateLimitConfig {

    @Bean
    @Primary
    public KeyResolver tenantKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(token -> {
                    String tenantId = token.getToken().getClaimAsString("tid");
                    return tenantId != null ? "tenant:" + tenantId : "user:" + token.getName();
                })
                .switchIfEmpty(Mono.just(clientAddress(exchange)));
    }

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(clientAddress(exchange));
    }

    private String clientAddress(org.springframework.web.server.ServerWebExchange exchange) {
        var remote = exchange.getRequest().getRemoteAddress();
        return "ip:" + (remote == null ? "unknown" : remote.getAddress().getHostAddress());
    }

    /** Unused placeholder kept out: authentication is resolved through the security filter chain. */
    private Authentication unused() {
        return null;
    }
}

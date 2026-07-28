package com.mfin.common.tenant;

import com.mfin.common.error.TenantResolutionException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Binds the tenant to the request thread from <em>verified</em> JWT claims.
 *
 * <p>Runs after the bearer-token filter has validated the token signature, so by the time this
 * executes the claims are trustworthy. A tenant id in the request body or query string is never
 * consulted; the only client-supplied input honoured here is the platform-admin override header,
 * and only for callers who actually hold {@link Roles#PLATFORM_ADMIN}.</p>
 */
@Component
@Order(TenantContextFilter.ORDER)
public class TenantContextFilter extends OncePerRequestFilter {

    /** Late enough to sit after Spring Security's filter chain. */
    public static final int ORDER = org.springframework.core.Ordered.LOWEST_PRECEDENCE - 100;

    private static final String MDC_TENANT = "tenantId";
    private static final String MDC_USER = "userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken token) {
                TenantPrincipal principal = fromToken(token, request);
                TenantContext.set(principal);
                if (principal.tenantId() != null) {
                    MDC.put(MDC_TENANT, principal.tenantId().toString());
                }
                if (principal.userId() != null) {
                    MDC.put(MDC_USER, principal.userId().toString());
                }
            }
            chain.doFilter(request, response);
        } finally {
            // Always unbind: these threads are pooled and reused across tenants.
            TenantContext.clear();
            MDC.remove(MDC_TENANT);
            MDC.remove(MDC_USER);
        }
    }

    private TenantPrincipal fromToken(JwtAuthenticationToken token, HttpServletRequest request) {
        Jwt jwt = token.getToken();
        Set<String> roles = new LinkedHashSet<>();
        for (GrantedAuthority authority : token.getAuthorities()) {
            String name = authority.getAuthority();
            roles.add(name.startsWith("ROLE_") ? name.substring("ROLE_".length()) : name);
        }
        boolean platformAdmin = roles.contains(Roles.PLATFORM_ADMIN);

        UUID tenantId = parseUuid(jwt.getClaimAsString(TenantClaims.TENANT_ID), TenantClaims.TENANT_ID);
        if (platformAdmin) {
            // Platform operators are not bound to one institution; they select the tenant they
            // are acting on with an explicit header, which is audited on every write.
            String override = request.getHeader(TenantClaims.TENANT_OVERRIDE_HEADER);
            if (override != null && !override.isBlank()) {
                tenantId = parseUuid(override, TenantClaims.TENANT_OVERRIDE_HEADER);
            }
        }

        return new TenantPrincipal(
                tenantId,
                parseUuid(jwt.getSubject(), "sub"),
                jwt.getClaimAsString(TenantClaims.USERNAME),
                roles,
                parseUuid(jwt.getClaimAsString(TenantClaims.BRANCH_ID), TenantClaims.BRANCH_ID),
                platformAdmin);
    }

    private UUID parseUuid(String value, String source) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new TenantResolutionException("Malformed identifier in " + source);
        }
    }
}

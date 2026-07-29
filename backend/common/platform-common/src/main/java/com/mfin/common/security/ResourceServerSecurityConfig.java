package com.mfin.common.security;

import com.mfin.common.error.ApiError;
import com.mfin.common.error.ErrorCodes;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mfin.common.tenant.TenantClaims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Baseline resource-server posture shared by every business service.
 *
 * <p>Stateless bearer-token authentication, CSRF disabled (there are no cookies or sessions to
 * forge against), everything denied unless explicitly permitted, and roles derived from the
 * {@code roles} claim of a signature-verified JWT.</p>
 *
 * <p>Services layer their own rules on top by defining a {@link SecurityFilterChain} bean,
 * which replaces this one via {@link ConditionalOnMissingBean}.</p>
 */
@Configuration
@EnableMethodSecurity
public class ResourceServerSecurityConfig {

    /** Endpoints that must stay reachable without a token. */
    public static final String[] PUBLIC_PATHS = {
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/prometheus",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    @Value("${mfin.security.cors.allowed-origins:}")
    private List<String> allowedOrigins;

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain defaultFilterChain(HttpSecurity http, ObjectMapper objectMapper)
            throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(problemEntryPoint(objectMapper))
                        .accessDeniedHandler(problemAccessDeniedHandler(objectMapper)))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(withDefaults -> {
                        })
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)))
                .build();
    }

    /**
     * Maps the {@code roles} claim onto Spring Security authorities.
     *
     * <p>Deliberately an anonymous class rather than a lambda. Spring's MVC conversion service
     * collects every {@code Converter} bean and reflects on its generic parameters; a lambda
     * erases them, and the context fails to start with "Unable to determine source type &lt;S&gt;
     * and target type &lt;T&gt; for your Converter".</p>
     */
    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        return new Converter<Jwt, AbstractAuthenticationToken>() {
            @Override
            public AbstractAuthenticationToken convert(Jwt jwt) {
                Collection<GrantedAuthority> authorities = extractRoles(jwt).stream()
                        .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                        .toList();
                return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
            }
        };
    }

    private List<String> extractRoles(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(TenantClaims.ROLES);
        return roles == null ? List.of() : roles;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Mobile clients send no Origin; browsers do, and only the configured ones are allowed.
        config.setAllowedOrigins(allowedOrigins == null ? List.of() : allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key",
                TenantClaims.TENANT_OVERRIDE_HEADER, "X-Request-Id"));
        config.setExposedHeaders(List.of("X-Request-Id"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** 401s are rendered in the platform's error shape rather than Spring's default empty body. */
    private org.springframework.security.web.AuthenticationEntryPoint problemEntryPoint(
            ObjectMapper objectMapper) {
        return (request, response, authException) -> writeError(objectMapper, response,
                HttpStatus.UNAUTHORIZED, ErrorCodes.UNAUTHENTICATED,
                "A valid access token is required", request.getRequestURI());
    }

    private org.springframework.security.web.access.AccessDeniedHandler problemAccessDeniedHandler(
            ObjectMapper objectMapper) {
        return (request, response, deniedException) -> writeError(objectMapper, response,
                HttpStatus.FORBIDDEN, ErrorCodes.ACCESS_DENIED,
                "You are not permitted to perform this operation", request.getRequestURI());
    }

    private void writeError(ObjectMapper objectMapper,
                            jakarta.servlet.http.HttpServletResponse response,
                            HttpStatus status, String code, String message, String path)
            throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError error = new ApiError(Instant.now(), status.value(), code, message, path,
                org.slf4j.MDC.get("traceId"), null);
        objectMapper.writeValue(response.getOutputStream(), error);
    }
}

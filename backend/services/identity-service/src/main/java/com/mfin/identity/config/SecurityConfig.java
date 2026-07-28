package com.mfin.identity.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mfin.common.security.ResourceServerSecurityConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * The identity service is both an authorisation server (it issues tokens) and a resource server
 * (its user-administration API is protected by them), so its chain permits the sign-in and key
 * endpoints while locking down everything else.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain identityFilterChain(HttpSecurity http,
                                                   ObjectMapper objectMapper,
                                                   CorsConfigurationSource corsConfigurationSource,
                                                   Converter<Jwt, AbstractAuthenticationToken> jwtConverter)
            throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(ResourceServerSecurityConfig.PUBLIC_PATHS).permitAll()
                        // Credential endpoints must be reachable without a token, by definition.
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/login",
                                "/api/v1/auth/platform-login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/oauth2/jwks",
                                "/.well-known/openid-configuration").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt
                        .jwtAuthenticationConverter(jwtConverter)))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)))
                .build();
    }
}

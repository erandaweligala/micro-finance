package com.mfin.common.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Shared OpenAPI document setup: bearer-token security scheme, service identity, and the
 * gateway server entry so "Try it out" in Swagger UI hits the same URL the mobile app does.
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name:microfinance-service}")
    private String applicationName;

    @Value("${mfin.openapi.gateway-url:http://localhost:8080}")
    private String gatewayUrl;

    @Bean
    public OpenAPI platformOpenApi() {
        final String bearer = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title(applicationName)
                        .version("1.0.0")
                        .description("""
                                Multi-tenant microfinance platform API.

                                All endpoints are tenant-scoped: the tenant is taken from the `tid`
                                claim of the access token, never from the request body. Platform
                                administrators select a tenant with the `X-Tenant-Id` header.

                                Write endpoints that move money accept an `Idempotency-Key` header
                                and are safe to retry.
                                """)
                        .contact(new Contact().name("Platform Engineering"))
                        .license(new License().name("Proprietary")))
                .servers(List.of(new Server().url(gatewayUrl).description("API gateway")))
                .components(new Components().addSecuritySchemes(bearer, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("OAuth 2.0 access token issued by the identity service")))
                .addSecurityItem(new SecurityRequirement().addList(bearer));
    }
}

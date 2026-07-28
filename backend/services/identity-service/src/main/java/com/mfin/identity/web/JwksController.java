package com.mfin.identity.web;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/**
 * Publishes the public half of the signing key so every resource server can verify tokens
 * offline, and discovery metadata so standard OAuth 2.0 clients can configure themselves.
 */
@RestController
@Tag(name = "OAuth 2.0", description = "Key and discovery endpoints")
public class JwksController {

    private final RSAKey signingKey;
    private final com.mfin.identity.config.IdentityProperties properties;

    public JwksController(RSAKey signingKey, com.mfin.identity.config.IdentityProperties properties) {
        this.signingKey = signingKey;
        this.properties = properties;
    }

    @GetMapping("/oauth2/jwks")
    @SecurityRequirements
    @Operation(summary = "JSON Web Key Set",
            description = "Public keys used to verify access tokens. Only the public half is "
                    + "exposed; the private key never leaves this service.")
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok()
                // Cached briefly: long enough to spare the round trip, short enough that a key
                // rotation propagates quickly.
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(new JWKSet(signingKey.toPublicJWK()).toJSONObject());
    }

    @GetMapping("/.well-known/openid-configuration")
    @SecurityRequirements
    @Operation(summary = "OpenID Connect discovery document")
    public Map<String, Object> discovery() {
        String issuer = properties.getIssuer();
        return Map.of(
                "issuer", issuer,
                "jwks_uri", issuer + "/oauth2/jwks",
                "token_endpoint", issuer + "/api/v1/auth/login",
                "id_token_signing_alg_values_supported", java.util.List.of("RS256"),
                "grant_types_supported", java.util.List.of("password", "refresh_token"),
                "response_types_supported", java.util.List.of("token"));
    }
}

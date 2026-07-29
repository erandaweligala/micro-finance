package com.mfin.identity.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.UUID;

/**
 * Token signing material and the password hashing strategy.
 *
 * <p>RS256 rather than HS256: resource servers must be able to <em>verify</em> tokens without
 * holding a key that would also let them <em>mint</em> them. Only this service has the private
 * key; everyone else fetches the public key from {@code /oauth2/jwks}.</p>
 */
@Configuration
public class JwtKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyConfig.class);

    @Bean
    public RSAKey rsaSigningKey(IdentityProperties properties) throws Exception {
        String pem = properties.getSigningKeyPem();
        if (pem == null || pem.isBlank()) {
            // Developer convenience only. An ephemeral key invalidates every token on restart
            // and cannot be shared across replicas, so production must supply a real one.
            log.warn("No mfin.identity.signing-key-pem configured - generating an EPHEMERAL RSA key. "
                    + "Tokens will not survive a restart and will not validate across replicas. "
                    + "Configure a key from your secret store before deploying.");
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            // A new key needs a new key id. Resource servers cache the JWKS and only re-fetch it
            // when they meet a kid they do not know; publishing fresh key material under the
            // configured id hands them a different key under an id they already hold, so they
            // keep verifying against the previous public key and reject every token this service
            // issues - until each one is restarted in turn. Only the ephemeral branch does this:
            // a configured key keeps its configured id, because it is stable across restarts.
            String ephemeralKeyId = properties.getSigningKeyId() + "-" + UUID.randomUUID();
            return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey(keyPair.getPrivate())
                    .keyID(ephemeralKeyId)
                    .build();
        }
        RSAPrivateKey privateKey = readPrivateKey(pem);
        RSAPublicKey publicKey = derivePublicKey(privateKey);
        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(properties.getSigningKeyId())
                .build();
    }

    /** Published at {@code /oauth2/jwks} so resource servers can rotate keys without a redeploy. */
    @Bean
    public JWKSource<SecurityContext> jwkSource(RSAKey rsaSigningKey) {
        return new ImmutableJWKSet<>(new JWKSet(rsaSigningKey));
    }

    /**
     * BCrypt at cost 12. Deliberately slow: the whole point of a password hash is that an
     * offline attacker with the database cannot test candidates quickly.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    private RSAPrivateKey readPrivateKey(String pem) throws Exception {
        String normalised = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(normalised);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    private RSAPublicKey derivePublicKey(RSAPrivateKey privateKey) throws Exception {
        if (privateKey instanceof java.security.interfaces.RSAPrivateCrtKey crtKey) {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) factory.generatePublic(
                    new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent()));
        }
        throw new IllegalStateException(
                "The configured signing key does not carry CRT parameters; supply a PKCS#8 RSA key");
    }
}

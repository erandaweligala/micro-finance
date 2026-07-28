package com.mfin.identity.application;

import com.mfin.common.error.ApiExceptions;
import com.mfin.common.tenant.TenantClaims;
import com.mfin.identity.config.IdentityProperties;
import com.mfin.identity.domain.AppUser;
import com.mfin.identity.domain.RefreshToken;
import com.mfin.identity.repository.IdentityRepositories.AppUserRepository;
import com.mfin.identity.repository.IdentityRepositories.RefreshTokenRepository;
import com.mfin.identity.web.dto.AuthDtos.TokenResponse;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Mints and rotates tokens.
 *
 * <p>The access token carries the tenant (`tid`) and roles, and is what every other service
 * trusts for authorisation. Because it is signed by this service's private key and short-lived,
 * a caller cannot alter their own tenant or role set - which is the foundation the entire
 * multi-tenant isolation model rests on.</p>
 */
@Service
public class TokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int REFRESH_TOKEN_BYTES = 48;

    private final RSAKey signingKey;
    private final IdentityProperties properties;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AppUserRepository userRepository;

    public TokenService(RSAKey signingKey, IdentityProperties properties,
                        RefreshTokenRepository refreshTokenRepository,
                        AppUserRepository userRepository) {
        this.signingKey = signingKey;
        this.properties = properties;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
    }

    /**
     * Resolves the account a refresh token belongs to.
     *
     * <p>The identity comes from the stored token row, never from anything the client sends
     * alongside it - otherwise a valid token for one user could be paired with another user's id.</p>
     */
    @Transactional(readOnly = true)
    public AppUser resolveUserForRefresh(String presentedToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(presentedToken))
                .orElseThrow(() -> new ApiExceptions.AccessDeniedException("Invalid refresh token"));
        return userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new ApiExceptions.AccessDeniedException("Invalid refresh token"));
    }

    /** Issues a fresh access/refresh pair for a new sign-in. */
    @Transactional
    public TokenResponse issue(AppUser user, String tenantSlug, String userAgent, String ipAddress) {
        return issue(user, tenantSlug, UUID.randomUUID(), userAgent, ipAddress);
    }

    /**
     * Rotates a refresh token.
     *
     * <p>If the presented token has already been used, the entire family is revoked: either the
     * token was stolen and replayed, or the legitimate client is racing itself. Both cases are
     * safest resolved by forcing a fresh sign-in.</p>
     */
    @Transactional
    public TokenResponse refresh(String presentedToken, AppUser user, String tenantSlug,
                                 String userAgent, String ipAddress) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(presentedToken))
                .orElseThrow(() -> new ApiExceptions.AccessDeniedException("Invalid refresh token"));

        if (stored.getUsedAt() != null) {
            refreshTokenRepository.revokeFamily(stored.getFamilyId(), Instant.now());
            throw new ApiExceptions.AccessDeniedException(
                    "This refresh token has already been used; please sign in again");
        }
        if (!stored.isActive()) {
            throw new ApiExceptions.AccessDeniedException("Refresh token is expired or revoked");
        }
        if (!stored.getUserId().equals(user.getId())) {
            throw new ApiExceptions.AccessDeniedException("Refresh token does not belong to this user");
        }

        stored.markUsed();
        return issue(user, tenantSlug, stored.getFamilyId(), userAgent, ipAddress);
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
    }

    @Transactional
    public void revoke(String presentedToken) {
        // Revoking an unknown token is a no-op rather than an error: it must not become an
        // oracle telling an attacker which tokens exist.
        refreshTokenRepository.findByTokenHash(hash(presentedToken))
                .ifPresent(RefreshToken::revoke);
    }

    private TokenResponse issue(AppUser user, String tenantSlug, UUID familyId,
                                String userAgent, String ipAddress) {
        Instant now = Instant.now();
        Instant accessExpiry = now.plus(properties.getAccessTokenTtl());
        String accessToken = signAccessToken(user, tenantSlug, now, accessExpiry);

        String refreshToken = randomToken();
        Instant refreshExpiry = now.plus(properties.getRefreshTokenTtl());
        refreshTokenRepository.save(new RefreshToken(user.getId(), user.getTenantId(),
                hash(refreshToken), familyId, refreshExpiry, truncate(userAgent), ipAddress));

        return new TokenResponse(
                accessToken,
                refreshToken,
                "Bearer",
                properties.getAccessTokenTtl().toSeconds(),
                user.getId(),
                user.getTenantId(),
                tenantSlug,
                user.getUsername(),
                user.getFullName(),
                List.copyOf(user.getRoles()),
                user.isMustChangePassword());
    }

    private String signAccessToken(AppUser user, String tenantSlug, Instant issuedAt, Instant expiry) {
        try {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .subject(user.getId().toString())
                    .issuer(properties.getIssuer())
                    .audience(properties.getAudience())
                    .issueTime(Date.from(issuedAt))
                    .expirationTime(Date.from(expiry))
                    .jwtID(UUID.randomUUID().toString())
                    .claim(TenantClaims.USERNAME, user.getUsername())
                    .claim(TenantClaims.ROLES, List.copyOf(user.getRoles()));

            // Absent for platform administrators, who select a tenant per request instead.
            if (user.getTenantId() != null) {
                claims.claim(TenantClaims.TENANT_ID, user.getTenantId().toString());
            }
            if (tenantSlug != null) {
                claims.claim(TenantClaims.TENANT_SLUG, tenantSlug);
            }
            if (user.getBranchId() != null) {
                claims.claim(TenantClaims.BRANCH_ID, user.getBranchId().toString());
            }
            if (user.getCustomerId() != null) {
                claims.claim("cid", user.getCustomerId().toString());
            }

            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(signingKey.getKeyID())
                            .type(JOSEObjectType.JWT)
                            .build(),
                    claims.build());
            jwt.sign(new RSASSASigner(signingKey.toRSAPrivateKey()));
            return jwt.serialize();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign access token", ex);
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Tokens are stored as digests so a database dump yields nothing usable. */
    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 255 ? value.substring(0, 255) : value;
    }
}

package com.mfin.identity.domain;

import com.mfin.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A refresh token, stored only as a SHA-256 digest.
 *
 * <p>Storing the digest means a database compromise does not hand the attacker usable tokens.
 * Rotation is enforced: redeeming a token marks it used and issues a new one, and presenting
 * an already-used token revokes the whole family, which is the standard detection for a stolen
 * refresh token being replayed.</p>
 */
@Entity
@Table(name = "refresh_token", indexes = {
        @Index(name = "ix_refresh_token_hash", columnList = "token_hash", unique = true),
        @Index(name = "ix_refresh_token_user", columnList = "user_id"),
        @Index(name = "ix_refresh_token_family", columnList = "family_id")
})
public class RefreshToken extends BaseEntity {

    @Column(name = "user_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID userId;

    @Column(name = "tenant_id", columnDefinition = "CHAR(36)")
    private UUID tenantId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    /** Shared by every token descended from one sign-in, so the chain can be revoked together. */
    @Column(name = "family_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID familyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** Recorded for the "where you are signed in" screen and for incident response. */
    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    protected RefreshToken() {
    }

    public RefreshToken(UUID userId, UUID tenantId, String tokenHash, UUID familyId,
                        Instant expiresAt, String userAgent, String ipAddress) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.expiresAt = expiresAt;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
    }

    public boolean isActive() {
        return usedAt == null && revokedAt == null && expiresAt.isAfter(Instant.now());
    }

    public void markUsed() {
        this.usedAt = Instant.now();
    }

    public void revoke() {
        this.revokedAt = Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}

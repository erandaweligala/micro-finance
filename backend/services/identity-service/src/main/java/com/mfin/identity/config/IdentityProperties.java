package com.mfin.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Tunable identity policy. Defaults are the production posture, not a permissive one. */
@ConfigurationProperties(prefix = "mfin.identity")
public class IdentityProperties {

    /** Token issuer; must match the {@code issuer-uri} configured on every resource server. */
    private String issuer = "http://localhost:8081";

    /** Audience claim, checked by resource servers. */
    private String audience = "mfin-api";

    /** Short-lived by design: revocation is handled by refusing to refresh. */
    private Duration accessTokenTtl = Duration.ofMinutes(15);

    private Duration refreshTokenTtl = Duration.ofDays(14);

    private Duration passwordResetTtl = Duration.ofMinutes(30);

    /** Consecutive failures before the account is locked. */
    private int maxFailedLogins = 5;

    private int lockoutMinutes = 15;

    /** Force a password change after this many days; 0 disables expiry. */
    private int passwordMaxAgeDays = 90;

    /** PEM-encoded RSA private key used to sign tokens. Injected from a secret in production. */
    private String signingKeyPem;

    private String signingKeyId = "mfin-signing-key";

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public void setRefreshTokenTtl(Duration refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public Duration getPasswordResetTtl() {
        return passwordResetTtl;
    }

    public void setPasswordResetTtl(Duration passwordResetTtl) {
        this.passwordResetTtl = passwordResetTtl;
    }

    public int getMaxFailedLogins() {
        return maxFailedLogins;
    }

    public void setMaxFailedLogins(int maxFailedLogins) {
        this.maxFailedLogins = maxFailedLogins;
    }

    public int getLockoutMinutes() {
        return lockoutMinutes;
    }

    public void setLockoutMinutes(int lockoutMinutes) {
        this.lockoutMinutes = lockoutMinutes;
    }

    public int getPasswordMaxAgeDays() {
        return passwordMaxAgeDays;
    }

    public void setPasswordMaxAgeDays(int passwordMaxAgeDays) {
        this.passwordMaxAgeDays = passwordMaxAgeDays;
    }

    public String getSigningKeyPem() {
        return signingKeyPem;
    }

    public void setSigningKeyPem(String signingKeyPem) {
        this.signingKeyPem = signingKeyPem;
    }

    public String getSigningKeyId() {
        return signingKeyId;
    }

    public void setSigningKeyId(String signingKeyId) {
        this.signingKeyId = signingKeyId;
    }
}

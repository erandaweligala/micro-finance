package com.mfin.identity.domain;

import com.mfin.common.persistence.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A person who can sign in.
 *
 * <p>Not a {@code TenantAwareEntity}: platform administrators legitimately have no tenant, and
 * the login endpoint has to find a user <em>before</em> any tenant context exists. Tenant
 * scoping is therefore enforced explicitly in every query and in {@code UserService}.</p>
 */
@Entity
@Table(name = "app_user",
        uniqueConstraints = @UniqueConstraint(name = "ux_user_tenant_username",
                columnNames = {"tenant_id", "username"}),
        indexes = {
                @Index(name = "ix_user_email", columnList = "email"),
                @Index(name = "ix_user_tenant", columnList = "tenant_id")
        })
public class AppUser extends BaseEntity {

    /** Null only for platform administrators, who are not bound to an institution. */
    @Column(name = "tenant_id", columnDefinition = "CHAR(36)")
    private UUID tenantId;

    @Column(name = "username", nullable = false, length = 128)
    private String username;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(name = "phone_number", length = 32)
    private String phoneNumber;

    /** BCrypt hash. The plaintext never leaves the request thread. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "password_updated_at", nullable = false)
    private Instant passwordUpdatedAt = Instant.now();

    /** Forces a password change on next sign-in, e.g. after an admin reset. */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private UserStatus status = UserStatus.ACTIVE;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "app_user_role",
            joinColumns = @JoinColumn(name = "user_id", foreignKey =
                    @jakarta.persistence.ForeignKey(name = "fk_user_role_user")))
    @Column(name = "role", nullable = false, length = 32)
    private Set<String> roles = new LinkedHashSet<>();

    /** Branch the user is posted to; scopes what a loan officer or cashier can see. */
    @Column(name = "branch_id", columnDefinition = "CHAR(36)")
    private UUID branchId;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** Links a self-service borrower login to their customer record. */
    @Column(name = "customer_id", columnDefinition = "CHAR(36)")
    private UUID customerId;

    protected AppUser() {
    }

    public AppUser(UUID tenantId, String username, String email, String fullName,
                   String passwordHash, Set<String> roles) {
        this.tenantId = tenantId;
        this.username = username;
        this.email = email;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
        this.roles = new LinkedHashSet<>(roles);
    }

    /** True while a lockout window from repeated failures is still in force. */
    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    public boolean canSignIn() {
        return status == UserStatus.ACTIVE && !isLocked();
    }

    /**
     * Records a failed sign-in and locks the account once the threshold is reached.
     * Locking is time-boxed rather than permanent so that an attacker cannot trivially
     * deny service to a legitimate user forever.
     */
    public void registerFailedLogin(int maxAttempts, int lockMinutes) {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= maxAttempts) {
            this.lockedUntil = Instant.now().plus(lockMinutes, ChronoUnit.MINUTES);
            this.failedLoginAttempts = 0;
        }
    }

    public void registerSuccessfulLogin() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        this.lastLoginAt = Instant.now();
    }

    public void changePassword(String newHash) {
        this.passwordHash = newHash;
        this.passwordUpdatedAt = Instant.now();
        this.mustChangePassword = false;
    }

    public void resetPassword(String newHash, boolean forceChange) {
        changePassword(newHash);
        this.mustChangePassword = forceChange;
    }

    public void deactivate() {
        this.status = UserStatus.DEACTIVATED;
    }

    public void activate() {
        this.status = UserStatus.ACTIVE;
        this.lockedUntil = null;
        this.failedLoginAttempts = 0;
    }

    public void updateProfile(String fullName, String email, String phoneNumber, UUID branchId) {
        this.fullName = fullName;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.branchId = branchId;
    }

    public void replaceRoles(Set<String> newRoles) {
        this.roles = new LinkedHashSet<>(newRoles);
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getPasswordUpdatedAt() {
        return passwordUpdatedAt;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Set<String> getRoles() {
        return Set.copyOf(roles);
    }

    public UUID getBranchId() {
        return branchId;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }
}

package com.mfin.identity.domain;

import com.mfin.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

/**
 * Local projection of "which institutions exist and what are they called".
 *
 * <p>Sign-in is the most latency- and availability-sensitive path in the platform, so it must
 * not depend on a synchronous call to the tenant service. This read model is kept up to date
 * from {@code mfin.tenant} events; identity owns the copy, tenant-service owns the truth.</p>
 */
@Entity
@Table(name = "tenant_directory",
        uniqueConstraints = @UniqueConstraint(name = "ux_tenant_directory_slug", columnNames = "slug"))
public class TenantDirectoryEntry extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID tenantId;

    @Column(name = "slug", nullable = false, length = 64)
    private String slug;

    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;

    /** When false the institution is suspended and nobody may sign in to it. */
    @Column(name = "login_enabled", nullable = false)
    private boolean loginEnabled = true;

    protected TenantDirectoryEntry() {
    }

    public TenantDirectoryEntry(UUID tenantId, String slug, String displayName) {
        this.tenantId = tenantId;
        this.slug = slug;
        this.displayName = displayName;
    }

    public void update(String displayName, boolean loginEnabled) {
        this.displayName = displayName;
        this.loginEnabled = loginEnabled;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getSlug() {
        return slug;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isLoginEnabled() {
        return loginEnabled;
    }
}

package com.mfin.common.persistence;

import com.mfin.common.tenant.TenantContext;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.util.UUID;

/**
 * Base class for every tenant-owned table.
 *
 * <p>The tenant id is stamped from the request's verified principal on insert and is
 * {@code updatable = false}, so a row can never be moved between institutions - not by a
 * malicious payload, not by a buggy mapper.</p>
 *
 * <p>Concrete entities additionally carry
 * {@code @Filter(name = TenantAwareEntity.FILTER, condition = "tenant_id = :tenantId")};
 * Hibernate only honours {@code @Filter} on entity classes, which is why it cannot be
 * declared once here. See {@code TenantFilterAspect} for how the filter is switched on.</p>
 */
@MappedSuperclass
@FilterDef(name = TenantAwareEntity.FILTER,
        parameters = @ParamDef(name = TenantAwareEntity.FILTER_PARAM, type = UUID.class))
public abstract class TenantAwareEntity extends BaseEntity {

    public static final String FILTER = "tenantFilter";
    public static final String FILTER_PARAM = "tenantId";
    public static final String CONDITION = "tenant_id = :tenantId";

    @Column(name = "tenant_id", columnDefinition = "CHAR(36)", nullable = false, updatable = false)
    private UUID tenantId;

    @PrePersist
    void stampTenant() {
        if (tenantId == null) {
            tenantId = TenantContext.requireTenantId();
        }
    }

    public UUID getTenantId() {
        return tenantId;
    }

    /**
     * Only for tests and for controlled cross-tenant provisioning by platform operators.
     * Business code must let {@link #stampTenant()} derive it from the request principal.
     */
    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }
}

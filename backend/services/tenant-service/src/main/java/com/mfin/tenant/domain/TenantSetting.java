package com.mfin.tenant.domain;

import com.mfin.common.persistence.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Filter;

/**
 * A per-institution configuration value, e.g. the repayment allocation order, the working
 * calendar, or SMS sender id. Key/value rather than columns so a new setting does not need a
 * schema migration and a redeploy of every service.
 */
@Entity
@Table(name = "tenant_setting",
        uniqueConstraints = @UniqueConstraint(name = "ux_setting_tenant_key",
                columnNames = {"tenant_id", "setting_key"}))
@Filter(name = TenantAwareEntity.FILTER, condition = TenantAwareEntity.CONDITION)
public class TenantSetting extends TenantAwareEntity {

    @Column(name = "setting_key", nullable = false, length = 96, updatable = false)
    private String key;

    @Column(name = "setting_value", nullable = false, length = 1024)
    private String value;

    @Column(name = "description", length = 256)
    private String description;

    protected TenantSetting() {
    }

    public TenantSetting(String key, String value, String description) {
        this.key = key;
        this.value = value;
        this.description = description;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }
}

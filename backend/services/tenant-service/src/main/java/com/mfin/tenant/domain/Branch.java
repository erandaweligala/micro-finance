package com.mfin.tenant.domain;

import com.mfin.common.persistence.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Filter;

/** A branch or service point of an institution. Scopes what branch-level staff can see. */
@Entity
@Table(name = "branch",
        uniqueConstraints = @UniqueConstraint(name = "ux_branch_tenant_code",
                columnNames = {"tenant_id", "code"}),
        indexes = @Index(name = "ix_branch_tenant", columnList = "tenant_id"))
@Filter(name = TenantAwareEntity.FILTER, condition = TenantAwareEntity.CONDITION)
public class Branch extends TenantAwareEntity {

    @Column(name = "code", nullable = false, length = 24)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "address_line", length = 200)
    private String addressLine;

    @Column(name = "city", length = 96)
    private String city;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Branch() {
    }

    public Branch(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public void update(String name, String addressLine, String city, String phone) {
        this.name = name;
        this.addressLine = addressLine;
        this.city = city;
        this.phone = phone;
    }

    public void deactivate() {
        this.active = false;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public String getCity() {
        return city;
    }

    public String getPhone() {
        return phone;
    }

    public boolean isActive() {
        return active;
    }
}

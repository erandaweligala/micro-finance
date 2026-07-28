package com.mfin.common.tenant;

import java.util.Set;
import java.util.UUID;

/**
 * The authenticated caller, as resolved from verified JWT claims only.
 *
 * <p>Nothing in this object is ever populated from a request body or an unverified header:
 * the tenant a caller may act on is decided by the token issuer, not by the client.</p>
 *
 * @param tenantId  tenant the request operates on
 * @param userId    subject of the token
 * @param username  human-readable identifier, for audit records
 * @param roles     granted roles, without the {@code ROLE_} prefix
 * @param branchId  branch the user belongs to, null for tenant-wide users
 * @param platformAdmin true when the caller is a platform operator acting across tenants
 */
public record TenantPrincipal(
        UUID tenantId,
        UUID userId,
        String username,
        Set<String> roles,
        UUID branchId,
        boolean platformAdmin
) {

    public TenantPrincipal {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    /** A system principal for background jobs (outbox drain, schedulers) scoped to one tenant. */
    public static TenantPrincipal system(UUID tenantId) {
        return new TenantPrincipal(tenantId, null, "system", Set.of(Roles.SYSTEM), null, false);
    }
}

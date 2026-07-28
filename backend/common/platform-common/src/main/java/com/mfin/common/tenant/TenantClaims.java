package com.mfin.common.tenant;

/** Names of the custom JWT claims the platform issues and trusts. */
public final class TenantClaims {

    /** Tenant UUID. The single source of truth for tenant resolution. */
    public static final String TENANT_ID = "tid";

    /** Tenant slug, used for subdomain-based login and for display. */
    public static final String TENANT_SLUG = "tsl";

    /** Branch UUID the user is posted to. */
    public static final String BRANCH_ID = "bid";

    /** Granted roles, as a JSON array of strings. */
    public static final String ROLES = "roles";

    public static final String USERNAME = "preferred_username";

    /**
     * Header a platform administrator uses to select the tenant they are acting on.
     * Honoured <em>only</em> for callers holding {@link Roles#PLATFORM_ADMIN}.
     */
    public static final String TENANT_OVERRIDE_HEADER = "X-Tenant-Id";

    private TenantClaims() {
    }
}

package com.mfin.common.tenant;

/**
 * Platform roles. Used both in {@code @PreAuthorize} expressions and when minting tokens,
 * so the names live in exactly one place.
 */
public final class Roles {

    /** Operates the SaaS platform itself; the only role permitted to cross tenant boundaries. */
    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";

    /** Administers a single microfinance institution. */
    public static final String TENANT_ADMIN = "TENANT_ADMIN";

    public static final String BRANCH_MANAGER = "BRANCH_MANAGER";
    public static final String LOAN_OFFICER = "LOAN_OFFICER";
    public static final String CASHIER = "CASHIER";

    /** Read-only across the tenant, including audit trails. */
    public static final String AUDITOR = "AUDITOR";

    /** An end borrower using the self-service app. */
    public static final String CUSTOMER = "CUSTOMER";

    /** Internal service-to-service and scheduled-job identity. */
    public static final String SYSTEM = "SYSTEM";

    /** Spring Security expression fragments, kept here so controllers stay readable. */
    public static final class Has {
        public static final String PLATFORM_ADMIN = "hasRole('PLATFORM_ADMIN')";
        public static final String TENANT_ADMIN = "hasAnyRole('PLATFORM_ADMIN','TENANT_ADMIN')";
        public static final String MANAGEMENT = "hasAnyRole('PLATFORM_ADMIN','TENANT_ADMIN','BRANCH_MANAGER')";
        public static final String LOAN_WRITE = "hasAnyRole('TENANT_ADMIN','BRANCH_MANAGER','LOAN_OFFICER')";
        public static final String LOAN_APPROVE = "hasAnyRole('TENANT_ADMIN','BRANCH_MANAGER')";
        public static final String PAYMENT_WRITE = "hasAnyRole('TENANT_ADMIN','BRANCH_MANAGER','CASHIER')";
        public static final String PAYMENT_REVERSE = "hasAnyRole('TENANT_ADMIN','BRANCH_MANAGER')";
        public static final String READ = "hasAnyRole('PLATFORM_ADMIN','TENANT_ADMIN','BRANCH_MANAGER',"
                + "'LOAN_OFFICER','CASHIER','AUDITOR')";

        private Has() {
        }
    }

    private Roles() {
    }
}

package com.mfin.common.tenant;

import com.mfin.common.error.TenantResolutionException;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Holds the {@link TenantPrincipal} for the current thread.
 *
 * <p>Populated once per request by {@link TenantContextFilter} from verified token claims and
 * cleared in a {@code finally} block, so a pooled thread can never leak one tenant's identity
 * into another tenant's request.</p>
 */
public final class TenantContext {

    private static final ThreadLocal<TenantPrincipal> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(TenantPrincipal principal) {
        CURRENT.set(principal);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static Optional<TenantPrincipal> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** The current principal, or a 403 if the request somehow reached business code unauthenticated. */
    public static TenantPrincipal require() {
        TenantPrincipal principal = CURRENT.get();
        if (principal == null) {
            throw new TenantResolutionException("No tenant context bound to the current request");
        }
        return principal;
    }

    public static UUID requireTenantId() {
        UUID tenantId = require().tenantId();
        if (tenantId == null) {
            throw new TenantResolutionException("The authenticated principal has no tenant assigned");
        }
        return tenantId;
    }

    public static Optional<UUID> currentUserId() {
        return current().map(TenantPrincipal::userId);
    }

    /**
     * Runs {@code work} under a specific principal, restoring the previous one afterwards.
     * Used by schedulers and message consumers, which have no inbound request to derive from.
     */
    public static <T> T callAs(TenantPrincipal principal, Supplier<T> work) {
        TenantPrincipal previous = CURRENT.get();
        try {
            CURRENT.set(principal);
            return work.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static void runAs(TenantPrincipal principal, Runnable work) {
        callAs(principal, () -> {
            work.run();
            return null;
        });
    }
}

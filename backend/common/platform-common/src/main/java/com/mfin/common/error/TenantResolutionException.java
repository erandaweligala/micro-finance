package com.mfin.common.error;

import org.springframework.http.HttpStatus;

/**
 * Raised when a request reaches tenant-scoped code without a resolvable tenant.
 *
 * <p>Treated as 403 rather than 400: the caller authenticated, but the token does not entitle
 * them to operate on any institution, and we must not hint at which tenants exist.</p>
 */
public class TenantResolutionException extends ApiExceptions.ApiException {

    public TenantResolutionException(String message) {
        super(HttpStatus.FORBIDDEN, ErrorCodes.TENANT_RESOLUTION_FAILED, message);
    }
}

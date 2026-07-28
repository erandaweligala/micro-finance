package com.mfin.common.persistence;

import com.mfin.common.tenant.TenantContext;
import com.mfin.common.tenant.TenantPrincipal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Switches on the Hibernate tenant filter for the duration of every transaction, so that
 * {@code SELECT}s against tenant-owned tables carry a {@code tenant_id = ?} predicate even
 * when a developer forgets to write one.
 *
 * <p>This is <strong>defence in depth</strong>, not the primary control. Repository methods
 * still take the tenant id explicitly, because the Hibernate filter does not apply to native
 * queries, {@code find()} by primary key, or projections built outside the session. Both
 * layers must be wrong for data to leak.</p>
 *
 * <p>Ordering: {@code JpaAuditingConfig} pins transaction advice to order 100, so this aspect
 * at {@link Ordered#LOWEST_PRECEDENCE} runs <em>inside</em> an open transaction where the
 * session is guaranteed to exist.</p>
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class TenantFilterAspect {

    private static final Logger log = LoggerFactory.getLogger(TenantFilterAspect.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Before("@within(org.springframework.transaction.annotation.Transactional) "
            + "|| @annotation(org.springframework.transaction.annotation.Transactional)")
    public void enableTenantFilter() {
        TenantPrincipal principal = TenantContext.current().orElse(null);
        if (principal == null || principal.tenantId() == null) {
            // No tenant bound: either an unauthenticated endpoint, or a platform operator
            // deliberately working across tenants. Those endpoints are PLATFORM_ADMIN-only.
            return;
        }
        try {
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter(TenantAwareEntity.FILTER)
                    .setParameter(TenantAwareEntity.FILTER_PARAM, principal.tenantId());
        } catch (RuntimeException ex) {
            // Never fail a request because the belt-and-braces layer could not attach.
            log.debug("Tenant filter not applied to this transaction: {}", ex.getMessage());
        }
    }
}

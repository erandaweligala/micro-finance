package com.mfin.common.persistence;

import com.mfin.common.tenant.TenantContext;
import com.mfin.common.tenant.TenantPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.time.Instant;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;

/**
 * Enables JPA auditing and pins transaction advice ahead of {@link TenantFilterAspect}.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware", dateTimeProviderRef = "auditingDateTimeProvider")
@EnableTransactionManagement(order = 100)
public class JpaAuditingConfig {

    /** Records who changed a row, from the verified principal rather than any request field. */
    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> TenantContext.current()
                .map(TenantPrincipal::username)
                .or(() -> Optional.of("system"));
    }

    /** UTC everywhere: financial timestamps must not move with a server's local zone. */
    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of((TemporalAccessor) Instant.now());
    }
}

package com.mfin.reporting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * reporting-audit-service entry point.
 *
 * <p>The component scan, entity scan and repository scan all include {@code com.mfin.common}
 * so the service inherits the shared tenancy, security, auditing and outbox infrastructure.</p>
 */
@SpringBootApplication(scanBasePackages = {"com.mfin.reporting", "com.mfin.common"})
@ConfigurationPropertiesScan(basePackages = {"com.mfin"})
@EntityScan(basePackages = {"com.mfin.reporting", "com.mfin.common"})
// considerNestedRepositories: the repository interfaces are grouped as nested
// types inside a container class, and Spring Data skips those by default -
// they are silently not registered rather than reported as an error.
@EnableJpaRepositories(basePackages = {"com.mfin.reporting", "com.mfin.common"}, considerNestedRepositories = true)
public class ReportingAuditServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportingAuditServiceApplication.class, args);
    }
}

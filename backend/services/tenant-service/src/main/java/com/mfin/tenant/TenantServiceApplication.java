package com.mfin.tenant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * tenant-service entry point.
 *
 * <p>The component scan, entity scan and repository scan all include {@code com.mfin.common}
 * so the service inherits the shared tenancy, security, auditing and outbox infrastructure.</p>
 */
@SpringBootApplication(scanBasePackages = {"com.mfin.tenant", "com.mfin.common"})
@ConfigurationPropertiesScan(basePackages = {"com.mfin"})
@EntityScan(basePackages = {"com.mfin.tenant", "com.mfin.common"})
@EnableJpaRepositories(basePackages = {"com.mfin.tenant", "com.mfin.common"})
public class TenantServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TenantServiceApplication.class, args);
    }
}

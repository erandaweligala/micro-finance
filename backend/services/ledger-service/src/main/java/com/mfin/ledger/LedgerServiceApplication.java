package com.mfin.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * ledger-service entry point.
 *
 * <p>The component scan, entity scan and repository scan all include {@code com.mfin.common}
 * so the service inherits the shared tenancy, security, auditing and outbox infrastructure.</p>
 */
@SpringBootApplication(scanBasePackages = {"com.mfin.ledger", "com.mfin.common"})
@ConfigurationPropertiesScan(basePackages = {"com.mfin"})
@EntityScan(basePackages = {"com.mfin.ledger", "com.mfin.common"})
@EnableJpaRepositories(basePackages = {"com.mfin.ledger", "com.mfin.common"})
public class LedgerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgerServiceApplication.class, args);
    }
}

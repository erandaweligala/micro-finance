package com.mfin.origination;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * loan-origination-service entry point.
 *
 * <p>The component scan, entity scan and repository scan all include {@code com.mfin.common}
 * so the service inherits the shared tenancy, security, auditing and outbox infrastructure.</p>
 */
@SpringBootApplication(scanBasePackages = {"com.mfin.origination", "com.mfin.common"})
@ConfigurationPropertiesScan(basePackages = {"com.mfin"})
@EntityScan(basePackages = {"com.mfin.origination", "com.mfin.common"})
@EnableJpaRepositories(basePackages = {"com.mfin.origination", "com.mfin.common"})
public class LoanOriginationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoanOriginationServiceApplication.class, args);
    }
}

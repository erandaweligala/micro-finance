package com.mfin.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * payment-service entry point.
 *
 * <p>The component scan, entity scan and repository scan all include {@code com.mfin.common}
 * so the service inherits the shared tenancy, security, auditing and outbox infrastructure.</p>
 */
@SpringBootApplication(scanBasePackages = {"com.mfin.payment", "com.mfin.common"})
@ConfigurationPropertiesScan(basePackages = {"com.mfin"})
@EntityScan(basePackages = {"com.mfin.payment", "com.mfin.common"})
@EnableJpaRepositories(basePackages = {"com.mfin.payment", "com.mfin.common"})
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}

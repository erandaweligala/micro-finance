package com.mfin.common.config;

import com.mfin.common.crypto.CryptoProperties;
import com.mfin.common.outbox.OutboxProperties;
import com.mfin.loan.engine.LoanCalculator;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wires the shared platform beans into any service that imports this library.
 *
 * <p>Services activate it by including {@code com.mfin.common} in their component scan; see
 * each service's {@code Application} class.</p>
 */
@Configuration
@EnableConfigurationProperties({CryptoProperties.class, OutboxProperties.class})
@EnableScheduling
public class PlatformCommonConfiguration {

    /** Stateless and thread-safe, so one instance serves the whole service. */
    @Bean
    public LoanCalculator loanCalculator() {
        return new LoanCalculator();
    }

    /**
     * Maps every {@code UUID} attribute onto a {@code CHAR(36)} column.
     *
     * <p>Every identifier in this schema is {@code CHAR(36)} - readable in a query result, and
     * greppable in a log. Hibernate's default on MySQL is {@code BINARY(16)}, and
     * {@code columnDefinition} does not change that: it governs generated DDL only, never the
     * JDBC binding. Left at the default, Hibernate writes sixteen raw bytes into a character
     * column and reads the first sixteen characters back as a UUID, so an id makes a silent
     * round trip into a different value - a foreign key lookup then matches nothing and the
     * caller sees an empty result rather than an error.</p>
     */
    @Bean
    public HibernatePropertiesCustomizer uuidAsCharJdbcType() {
        return properties -> properties.put(AvailableSettings.PREFERRED_UUID_JDBC_TYPE, "CHAR");
    }
}

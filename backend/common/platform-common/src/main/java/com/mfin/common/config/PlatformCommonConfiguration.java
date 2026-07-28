package com.mfin.common.config;

import com.mfin.common.crypto.CryptoProperties;
import com.mfin.common.outbox.OutboxProperties;
import com.mfin.loan.engine.LoanCalculator;
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
}

package com.mfin.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The platform's single ingress.
 *
 * <p>Reactive (Netty) rather than servlet-based, because a gateway spends nearly all its time
 * waiting on downstream I/O and a thread-per-request model would cap throughput at the size of
 * the thread pool. This is why it does not depend on {@code platform-common}.</p>
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}

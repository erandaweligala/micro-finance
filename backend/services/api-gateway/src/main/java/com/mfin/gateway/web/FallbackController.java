package com.mfin.gateway.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Served when a circuit breaker opens.
 *
 * <p>Returns the platform's standard error shape with a 503, so the mobile app can show
 * "temporarily unavailable, try again" rather than a raw gateway error - and so a retry is
 * clearly signalled as reasonable.</p>
 */
@RestController
public class FallbackController {

    @RequestMapping("/fallback/{service}")
    public Mono<ResponseEntity<Map<String, Object>>> fallback(
            @org.springframework.web.bind.annotation.PathVariable String service) {
        Map<String, Object> body = Map.of(
                "timestamp", Instant.now().toString(),
                "status", HttpStatus.SERVICE_UNAVAILABLE.value(),
                "code", "UPSTREAM_UNAVAILABLE",
                "message", "The " + service.replace('-', ' ')
                        + " is temporarily unavailable. Please try again shortly.");
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body));
    }
}

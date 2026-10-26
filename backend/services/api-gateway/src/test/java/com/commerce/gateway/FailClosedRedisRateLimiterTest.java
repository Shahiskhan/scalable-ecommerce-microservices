package com.commerce.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter.Response;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

class FailClosedRedisRateLimiterTest {
    private static final String HEADER = "X-RateLimit-Remaining";

    @Test void convertsSpringRedisFailureFallbackToServiceUnavailable() {
        var fallback = new Response(true, Map.of(HEADER, "-1"));
        StepVerifier.create(FailClosedRedisRateLimiter.enforceAvailable(fallback, HEADER))
                .expectErrorSatisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(503))
                .verify();
    }
    @Test void preservesSuccessfulQuotaDecision() {
        var allowed = new Response(true, Map.of(HEADER, "25"));
        StepVerifier.create(FailClosedRedisRateLimiter.enforceAvailable(allowed, HEADER)).expectNext(allowed).verifyComplete();
    }
    @Test void preservesQuotaExhaustionDecision() {
        var exhausted = new Response(false, Map.of(HEADER, "0"));
        StepVerifier.create(FailClosedRedisRateLimiter.enforceAvailable(exhausted, HEADER)).expectNext(exhausted).verifyComplete();
    }
    @Test void missingLimitMetadataFailsClosed() {
        StepVerifier.create(FailClosedRedisRateLimiter.enforceAvailable(new Response(true, Map.of()), HEADER))
                .expectError(ResponseStatusException.class).verify();
    }
}

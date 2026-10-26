package com.commerce.gateway;

import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/** Spring's limiter allows Redis failures with remaining=-1; reject that sentinel explicitly. */
final class FailClosedRedisRateLimiter extends RedisRateLimiter {
    FailClosedRedisRateLimiter(int replenishRate, long burstCapacity, int requestedTokens) {
        super(replenishRate, burstCapacity, requestedTokens);
        setIncludeHeaders(true);
    }

    @Override public Mono<Response> isAllowed(String routeId, String id) {
        return super.isAllowed(routeId, id).flatMap(response -> enforceAvailable(response, getRemainingHeader()));
    }

    static Mono<Response> enforceAvailable(Response response, String remainingHeader) {
        String remaining = response.getHeaders().get(remainingHeader);
        try {
            if (remaining != null && Long.parseLong(remaining) >= 0) return Mono.just(response);
        } catch (NumberFormatException malformedRemaining) {
            // A malformed or missing limit response is an unavailable limit store.
        }
        return Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Rate limit store unavailable"));
    }
}

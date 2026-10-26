package com.commerce.gateway;

import java.time.Duration;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

@Configuration
class RoutingConfiguration {
    @Bean RedisRateLimiter redisRateLimiter() { return new FailClosedRedisRateLimiter(20, 40, 1); }
    @Bean KeyResolver authenticatedUserKeyResolver() {
        return exchange -> exchange.getPrincipal().flatMap(principal -> Mono.justOrEmpty(principal.getName()));
    }

    @Bean RouteLocator routes(RouteLocatorBuilder builder, KeyResolver authenticatedUserKeyResolver) {
        return builder.routes()
                .route("users", route -> route.path("/api/users/**")
                        .filters(filters -> resilient(filters, "users", authenticatedUserKeyResolver)).uri("lb://user-service"))
                .route("products", route -> route.path("/api/products/**", "/api/cart/**")
                        .filters(filters -> resilient(filters, "products", authenticatedUserKeyResolver)).uri("lb://product-service"))
                .route("orders", route -> route.path("/api/orders/**")
                        .filters(filters -> resilient(filters, "orders", authenticatedUserKeyResolver)).uri("lb://order-service"))
                .route("notifications", route -> route.path("/api/notifications/**")
                        .filters(filters -> resilient(filters, "notifications", authenticatedUserKeyResolver)).uri("lb://notification-service"))
                .build();
    }

    private GatewayFilterSpec resilient(GatewayFilterSpec filters, String circuit, KeyResolver key) {
        return filters.requestRateLimiter(config -> config.setKeyResolver(key))
                .circuitBreaker(config -> config.setName(circuit)
                        .addStatusCode("500").addStatusCode("502").addStatusCode("503").addStatusCode("504"))
                .retry(config -> config.setRetries(1).setMethods(HttpMethod.GET)
                        .setStatuses(HttpStatus.BAD_GATEWAY, HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.GATEWAY_TIMEOUT)
                        .setBackoff(Duration.ofMillis(100), Duration.ofMillis(300), 2, false));
    }
}




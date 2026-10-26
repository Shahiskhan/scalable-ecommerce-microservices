package com.commerce.gateway.security;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Component
public final class TrustedContextFilter implements GlobalFilter, Ordered {
    private final InternalContextSigner signer;

    public TrustedContextFilter(@Value("${security.internal.secret}") String secret) { signer = new InternalContextSigner(secret); }

    @Override public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal().ofType(Authentication.class)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED)))
                .flatMap(principal -> {
            String user = principal.getName();
            String roles = principal.getAuthorities().stream().map(authority -> authority.getAuthority())
                    .filter(role -> role.startsWith("ROLE_")).map(role -> role.substring(5)).sorted().distinct()
                    .reduce((left, right) -> left + "," + right).orElse("USER");
            String timestamp = Long.toString(Instant.now().getEpochSecond());
            String requestId = exchange.getAttributeOrDefault(RequestCorrelationFilter.REQUEST_ID_ATTRIBUTE, UUID.randomUUID().toString());
            String signature = signer.sign(exchange.getRequest().getMethod().name(),
                    exchange.getRequest().getURI().getRawPath(), user, roles, timestamp, requestId);
            return chain.filter(exchange.mutate().request(request -> request.headers(headers -> {
                new ArrayList<>(headers.headerNames()).stream()
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith("x-internal-"))
                        .forEach(headers::remove);
                headers.remove(HttpHeaders.AUTHORIZATION);
                headers.set("X-Internal-User-Id", user);
                headers.set("X-Internal-Roles", roles);
                headers.set("X-Internal-Timestamp", timestamp);
                headers.set("X-Internal-Request-Id", requestId);
                headers.set("X-Internal-Signature", signature);
            })).build());
        });
    }

    @Override public int getOrder() { return -10; }
}



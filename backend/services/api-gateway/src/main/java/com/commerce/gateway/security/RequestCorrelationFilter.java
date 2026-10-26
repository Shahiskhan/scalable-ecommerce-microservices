package com.commerce.gateway.security;

import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Assigns an edge-generated support ID before authentication, including rejected requests. */
@Component
public final class RequestCorrelationFilter implements WebFilter, Ordered {
    public static final String REQUEST_ID_ATTRIBUTE = RequestCorrelationFilter.class.getName() + ".requestId";

    @Override public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = UUID.randomUUID().toString();
        exchange.getAttributes().put(REQUEST_ID_ATTRIBUTE, requestId);
        exchange.getResponse().getHeaders().set("X-Request-Id", requestId);
        return chain.filter(exchange.mutate().request(request -> request.headers(headers -> headers.set("X-Request-Id", requestId))).build());
    }
    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
}

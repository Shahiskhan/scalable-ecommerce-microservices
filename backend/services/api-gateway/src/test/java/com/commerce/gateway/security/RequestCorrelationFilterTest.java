package com.commerce.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class RequestCorrelationFilterTest {
    @Test void assignsServerGeneratedCorrelationBeforeAuthentication() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders").header("X-Request-Id", "attacker"));
        var forwarded = new AtomicReference<ServerWebExchange>();
        new RequestCorrelationFilter().filter(exchange, next -> { forwarded.set(next); return Mono.empty(); }).block();
        String requestId = exchange.getResponse().getHeaders().getFirst("X-Request-Id");
        assertThat(UUID.fromString(requestId)).isNotNull();
        assertThat(requestId).isNotEqualTo("attacker");
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-Request-Id")).isEqualTo(requestId);
        assertThat((String) exchange.getAttribute(RequestCorrelationFilter.REQUEST_ID_ATTRIBUTE)).isEqualTo(requestId);
    }
}

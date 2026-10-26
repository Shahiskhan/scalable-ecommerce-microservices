package com.commerce.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class TrustedContextFilterTest {
    private static final String SECRET = "test-secret-at-least-thirty-two-bytes-long";

    @Test void replacesSpoofedContextAndRemovesBearerToken() {
        var principal = UsernamePasswordAuthenticationToken.authenticated("customer-123", "",
                List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN")));
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders/123?x=1")
                .header("X-Internal-User-Id", "attacker").header("X-Internal-Anything", "spoof")
                .header("Authorization", "Bearer sensitive")).mutate().principal(Mono.just(principal)).build();
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        new TrustedContextFilter(SECRET).filter(exchange, next -> { captured.set(next); return Mono.empty(); }).block();
        var headers = captured.get().getRequest().getHeaders();
        assertThat(headers.getFirst("X-Internal-User-Id")).isEqualTo("customer-123");
        assertThat(headers.getFirst("X-Internal-Roles")).isEqualTo("ADMIN,USER");
        assertThat(headers.containsHeader("Authorization")).isFalse();
        assertThat(headers.containsHeader("X-Internal-Anything")).isFalse();
        String expected = new InternalContextSigner(SECRET).sign("GET", "/api/orders/123", "customer-123", "ADMIN,USER",
                headers.getFirst("X-Internal-Timestamp"), headers.getFirst("X-Internal-Request-Id"));
        assertThat(headers.getFirst("X-Internal-Signature")).isEqualTo(expected);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test void matchesIndependentDotNetInteroperabilityFixture() {
        assertThat(new InternalContextSigner(SECRET).sign("POST", "/api/orders", "customer-123", "ADMIN,USER",
                "1792972800", "00000000-0000-0000-0000-000000000001"))
                .isEqualTo("z71CEVrwpDZ7eOkE0JryR352y7DPxcksCsipIQ5ODCo");
    }
    @Test void refusesWeakSecrets() { assertThatThrownBy(() -> new InternalContextSigner("weak")).isInstanceOf(IllegalArgumentException.class); }
    @Test void signatureBindsIdentityAndPath() {
        var signer = new InternalContextSigner(SECRET);
        assertThat(signer.sign("GET", "/a", "u1", "USER", "1", "id"))
                .isNotEqualTo(signer.sign("GET", "/b", "u1", "USER", "1", "id"))
                .isNotEqualTo(signer.sign("GET", "/a", "u2", "USER", "1", "id"));
    }
}



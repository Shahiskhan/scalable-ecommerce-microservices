package com.commerce.gateway.security;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.WebFilterChainProxy;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GatewayAuthorizationTest {
    private final WebTestClient client;

    GatewayAuthorizationTest() {
        ReactiveJwtDecoder decoder = token -> {
            if (token.equals("invalid")) return Mono.error(new BadJwtException("invalid token"));
            return Mono.just(Jwt.withTokenValue(token).header("alg", "RS256").subject("customer-123")
                    .issuedAt(Instant.now().minusSeconds(30)).expiresAt(Instant.now().plusSeconds(300))
                    .claim("admin", token.equals("administrator")).build());
        };
        ServerHttpSecurity http = ServerHttpSecurity.http()
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtDecoder(decoder)));
        var chain = new SecurityConfiguration().security(http);
        client = WebTestClient.bindToController(new Endpoint())
                .webFilter(new WebFilterChainProxy(chain)).configureClient().responseTimeout(Duration.ofSeconds(30)).build();
    }

    @Test void healthProbeIsPublic() { client.get().uri("/actuator/health").exchange().expectStatus().isOk(); }
    @Test void userApiRequiresAuthentication() { client.get().uri("/api/users/me").exchange().expectStatus().isUnauthorized(); }
    @Test void invalidBearerTokenIsRejected() { client.get().uri("/api/orders/1").headers(headers -> headers.setBearerAuth("invalid")).exchange().expectStatus().isUnauthorized(); }
    @Test void authenticatedCustomerCanReadCatalog() { client.get().uri("/api/products/1").headers(headers -> headers.setBearerAuth("customer")).exchange().expectStatus().isOk(); }
    @Test void customerCannotMutateCatalog() { client.post().uri("/api/products").headers(headers -> headers.setBearerAuth("customer")).exchange().expectStatus().isForbidden(); }
    @Test void administratorCanMutateCatalog() { client.post().uri("/api/products").headers(headers -> headers.setBearerAuth("administrator")).exchange().expectStatus().isOk(); }
    @Test void authenticatedCustomerCanReadNotifications() { client.get().uri("/api/notifications").headers(headers -> headers.setBearerAuth("customer")).exchange().expectStatus().isOk(); }
    @Test void managementEndpointsStayPrivate() { client.get().uri("/actuator/env").headers(headers -> headers.setBearerAuth("administrator")).exchange().expectStatus().isForbidden(); }
    @Test void paymentApiHasNoPublicRoute() { client.post().uri("/api/payments").headers(headers -> headers.setBearerAuth("administrator")).exchange().expectStatus().isForbidden(); }

    @RestController
    static class Endpoint {
        @RequestMapping("/**") String success() { return "ok"; }
    }
}


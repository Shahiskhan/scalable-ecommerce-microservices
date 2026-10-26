package com.commerce.gateway.security;

import java.time.Clock;
import java.time.Duration;
import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import reactor.core.publisher.Mono;

@Configuration
public class SecurityConfiguration {
    @Bean ReactiveJwtDecoder jwtDecoder(@Value("${security.firebase.project-id}") String projectId) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                .withJwkSetUri("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com")
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .webClient(WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000).responseTimeout(Duration.ofSeconds(2)))).build())
                .build();
        decoder.setJwtValidator(new FirebaseTokenValidator(projectId, Clock.systemUTC()));
        return decoder;
    }

    @Bean SecurityWebFilterChain security(ServerHttpSecurity http) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(auth -> auth.pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/products/**").authenticated()
                        .pathMatchers("/api/products/**").hasRole("ADMIN")
                        .pathMatchers("/api/users/**", "/api/cart/**", "/api/orders/**", "/api/notifications/**").authenticated()
                        .anyExchange().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(token -> {
                    TreeSet<String> roles = new TreeSet<>(List.of("USER"));
                    if (Boolean.TRUE.equals(token.getClaim("admin"))) roles.add("ADMIN");
                    Object customRoles = token.getClaim("roles");
                    if (customRoles instanceof Collection<?> claims) {
                        claims.stream().filter(String.class::isInstance).map(String.class::cast)
                                .filter(role -> role.equals("ADMIN") || role.equals("USER")).forEach(roles::add);
                    }
                    return Mono.just(new JwtAuthenticationToken(token,
                            roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList()));
                }))).build();
    }
}



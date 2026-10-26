package com.commerce.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class FirebaseTokenValidatorTest {
    private static final Instant NOW = Instant.parse("2026-10-26T00:00:00Z");
    private final FirebaseTokenValidator validator = new FirebaseTokenValidator("commerce-test", Clock.fixed(NOW, ZoneOffset.UTC));
    private Jwt.Builder valid() {
        return Jwt.withTokenValue("test").header("alg", "RS256").header("kid", "key")
                .issuer("https://securetoken.google.com/commerce-test").audience(java.util.List.of("commerce-test"))
                .subject("customer-123").issuedAt(NOW.minusSeconds(30)).expiresAt(NOW.plusSeconds(60))
                .claim("auth_time", NOW.minusSeconds(30).getEpochSecond());
    }

    @Test void acceptsValidFirebaseClaims() { assertThat(validator.validate(valid().build()).hasErrors()).isFalse(); }
    @Test void rejectsWrongAudience() { assertThat(validator.validate(valid().audience(java.util.List.of("other-project")).build()).hasErrors()).isTrue(); }
    @Test void rejectsWrongIssuer() { assertThat(validator.validate(valid().issuer("https://attacker.invalid").build()).hasErrors()).isTrue(); }
    @Test void rejectsExpiredTokenWithoutGracePeriod() { assertThat(validator.validate(valid().expiresAt(NOW).build()).hasErrors()).isTrue(); }
    @Test void rejectsFutureIssueTime() { assertThat(validator.validate(valid().issuedAt(NOW.plusSeconds(1)).build()).hasErrors()).isTrue(); }
    @Test void rejectsFutureAuthentication() { assertThat(validator.validate(valid().claim("auth_time", NOW.plusSeconds(1).getEpochSecond()).build()).hasErrors()).isTrue(); }
    @Test void rejectsMissingAuthenticationTime() { assertThat(validator.validate(valid().claims(claims -> claims.remove("auth_time")).build()).hasErrors()).isTrue(); }
    @Test void rejectsDifferentAlgorithm() { assertThat(validator.validate(valid().header("alg", "HS256").build()).hasErrors()).isTrue(); }
    @Test void rejectsMissingExpiry() { assertThat(validator.validate(valid().claims(claims -> claims.remove("exp")).build()).hasErrors()).isTrue(); }
    @Test void rejectsMissingIssueTime() { assertThat(validator.validate(valid().claims(claims -> claims.remove("iat")).build()).hasErrors()).isTrue(); }
    @Test void rejectsMissingKeyId() { assertThat(validator.validate(valid().headers(headers -> headers.remove("kid")).build()).hasErrors()).isTrue(); }
    @Test void rejectsFutureNotBefore() { assertThat(validator.validate(valid().notBefore(NOW.plusSeconds(1)).build()).hasErrors()).isTrue(); }
    @Test void rejectsInvalidAuthenticationTimeType() { assertThat(validator.validate(valid().claim("auth_time", "not-a-timestamp").build()).hasErrors()).isTrue(); }
    @Test void rejectsBlankSubject() { assertThat(validator.validate(valid().subject(" ").build()).hasErrors()).isTrue(); }
}


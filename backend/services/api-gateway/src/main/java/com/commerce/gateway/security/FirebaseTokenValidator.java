package com.commerce.gateway.security;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** Firebase ID-token claim checks in addition to the decoder's RSA signature check. */
public final class FirebaseTokenValidator implements OAuth2TokenValidator<Jwt> {
    private final String projectId;
    private final Clock clock;

    public FirebaseTokenValidator(String projectId, Clock clock) {
        if (projectId == null || !projectId.matches("[a-z][a-z0-9-]{4,61}[a-z0-9]")) {
            throw new IllegalArgumentException("A real Firebase project ID is required");
        }
        this.projectId = projectId;
        this.clock = clock;
    }

    @Override public OAuth2TokenValidatorResult validate(Jwt token) {
        Instant now = clock.instant();
        try {
            boolean valid = "RS256".equals(token.getHeaders().get("alg"))
                    && token.getHeaders().get("kid") instanceof String kid && !kid.isBlank()
                    && ("https://securetoken.google.com/" + projectId).equals(token.getClaimAsString("iss"))
                    && token.getAudience().equals(List.of(projectId))
                    && token.getSubject() != null && !token.getSubject().isBlank()
                    && token.getSubject().length() <= 128 && !token.getSubject().contains("\n")
                    && !token.getSubject().contains("\r")
                    && token.getSubject().codePoints().noneMatch(Character::isISOControl)
                    && token.getExpiresAt() != null && token.getExpiresAt().isAfter(now)
                    && token.getIssuedAt() != null && !token.getIssuedAt().isAfter(now)
                    && token.getIssuedAt().isBefore(token.getExpiresAt())
                    && (token.getNotBefore() == null || !token.getNotBefore().isAfter(now))
                    && token.getClaimAsInstant("auth_time") != null
                    && !token.getClaimAsInstant("auth_time").isAfter(now);
            if (valid) return OAuth2TokenValidatorResult.success();
        } catch (RuntimeException invalidClaimType) {
            // Malformed claims are authentication failures, never server errors.
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid Firebase ID token", null));
    }
}


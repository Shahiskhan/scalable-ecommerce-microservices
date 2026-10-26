package com.acme.commerce.notification.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/** Verifies gateway provenance, never verifies or accepts Firebase tokens. Use internal TLS. */
@Component
public final class InternalContextFilter extends OncePerRequestFilter {
    private final byte[] secret;
    private final java.time.Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public InternalContextFilter(@Value("${security.internal-hmac-secret}") String secret) { this(secret, java.time.Clock.systemUTC()); }
    public InternalContextFilter(String secret, java.time.Clock clock) {
        this.clock = clock;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        if (this.secret.length < 32) throw new IllegalArgumentException("Internal HMAC secret must be at least 32 bytes");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/")) {
            if (!request.getRequestURI().startsWith("/actuator/health")) { response.sendError(404); return; }
            chain.doFilter(request, response); return;
        }
        String user = request.getHeader("X-Internal-User-Id");
        String roles = request.getHeader("X-Internal-Roles");
        String timestamp = request.getHeader("X-Internal-Timestamp");
        String id = request.getHeader("X-Internal-Request-Id");
        String signature = request.getHeader("X-Internal-Signature");
        try {
            if (user == null || user.isBlank() || user.length() > 128 || user.chars().anyMatch(c -> c < 32)
                    || roles == null || roles.length() > 512 || timestamp == null || id == null || signature == null)
                throw new IllegalArgumentException();
            for (String header : java.util.List.of("X-Internal-User-Id", "X-Internal-Roles", "X-Internal-Timestamp", "X-Internal-Request-Id", "X-Internal-Signature")) {
                if (java.util.Collections.list(request.getHeaders(header)).size() != 1) throw new IllegalArgumentException();
            }
            UUID.fromString(id);
            long signedTime = Long.parseLong(timestamp), now = clock.instant().getEpochSecond();
            if (signedTime < now - 30 || signedTime > now + 30) throw new IllegalArgumentException();
            String canonical = String.join("\n", request.getMethod(), request.getRequestURI(), user, roles, timestamp, id);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            if (!MessageDigest.isEqual(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)), Base64.getUrlDecoder().decode(signature)))
                throw new IllegalArgumentException();
        } catch (Exception invalid) {
            response.setStatus(401); response.setContentType("application/problem+json");
            response.getWriter().write("{\"title\":\"Untrusted internal context\",\"status\":401}"); return;
        }
        request.setAttribute("userId", user);
        MDC.put("requestId", id);
        try { chain.doFilter(request, response); } finally { MDC.remove("requestId"); }
    }
}

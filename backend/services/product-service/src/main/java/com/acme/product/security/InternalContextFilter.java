package com.acme.product.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.Collections;
import java.util.UUID;

/** Verifies gateway context. Internal TLS and network policies remain required. */
@Component
public class InternalContextFilter extends OncePerRequestFilter {
    public static final String USER_ATTRIBUTE = "authenticatedUserId";
    private final byte[] key;
    private final Clock clock;
    @Autowired
    public InternalContextFilter(@Value("${commerce.internal-hmac-secret}") String secret) { this(secret, Clock.systemUTC()); }
    InternalContextFilter(String secret, Clock clock) {
        key = secret.getBytes(StandardCharsets.UTF_8);
        if (key.length < 32) throw new IllegalArgumentException("INTERNAL_HMAC_SECRET must be at least 32 bytes");
        this.clock = clock;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) { return !request.getRequestURI().startsWith("/api/"); }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        try {
            String user = one(request, "X-Internal-User-Id");
            String roles = one(request, "X-Internal-Roles");
            String timestamp = one(request, "X-Internal-Timestamp");
            String requestId = one(request, "X-Internal-Request-Id");
            String signature = one(request, "X-Internal-Signature");
            if (user.isBlank() || user.length() > 128 || user.contains("\n") || user.contains("\r")
                    || !roles.matches("[A-Z_]+(,[A-Z_]+)*")) throw new IllegalArgumentException();
            UUID.fromString(requestId);
            long seconds = Long.parseLong(timestamp), now = clock.instant().getEpochSecond();
            if (seconds < now - 30 || seconds > now + 30) throw new IllegalArgumentException();
            String canonical = request.getMethod() + "\n" + request.getRequestURI() + "\n" + user + "\n" + roles + "\n" + timestamp + "\n" + requestId;
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            if (!MessageDigest.isEqual(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)), Base64.getUrlDecoder().decode(signature))) throw new IllegalArgumentException();
            request.setAttribute(USER_ATTRIBUTE, user);
            request.setAttribute("authenticatedRoles", roles);
        } catch (Exception invalid) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid internal identity");
            return;
        }
        chain.doFilter(request, response);
    }
    private static String one(HttpServletRequest request, String name) {
        var values = Collections.list(request.getHeaders(name));
        if (values.size() != 1) throw new IllegalArgumentException();
        return values.getFirst();
    }
}

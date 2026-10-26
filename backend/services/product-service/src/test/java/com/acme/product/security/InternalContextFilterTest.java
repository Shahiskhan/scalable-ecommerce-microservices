package com.acme.product.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class InternalContextFilterTest {
    private final String secret = "test-only-secret-at-least-thirty-two-bytes";
    private final Instant now = Instant.parse("2026-10-26T12:00:00Z");
    private final InternalContextFilter filter = new InternalContextFilter(secret, Clock.fixed(now, ZoneOffset.UTC));
    private MockHttpServletRequest signed(long timestamp) throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/products/A");
        String id = UUID.randomUUID().toString(), time = Long.toString(timestamp);
        request.addHeader("X-Internal-User-Id", "buyer"); request.addHeader("X-Internal-Roles", "USER");
        request.addHeader("X-Internal-Timestamp", time); request.addHeader("X-Internal-Request-Id", id);
        var mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String canonical = "GET\n/api/products/A\nbuyer\nUSER\n" + time + "\n" + id;
        request.addHeader("X-Internal-Signature", Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8))));
        return request;
    }
    @Test void validContextSetsPrincipal() throws Exception {
        var request = signed(now.getEpochSecond()); var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertEquals(200, response.getStatus()); assertEquals("buyer", request.getAttribute(InternalContextFilter.USER_ATTRIBUTE));
    }
    @Test void rejectsStaleAndPathTamperedContexts() throws Exception {
        var stale = new MockHttpServletResponse(); filter.doFilter(signed(now.minusSeconds(31).getEpochSecond()), stale, new MockFilterChain());
        assertEquals(401, stale.getStatus());
        var request = signed(now.getEpochSecond()); request.setRequestURI("/api/products/B");
        var tampered = new MockHttpServletResponse(); filter.doFilter(request, tampered, new MockFilterChain());
        assertEquals(401, tampered.getStatus());
    }
    @Test void rejectsHeaderSpoofAndDuplicateHeader() throws Exception {
        var missing = new MockHttpServletResponse(); filter.doFilter(new MockHttpServletRequest("GET", "/api/cart"), missing, new MockFilterChain());
        assertEquals(401, missing.getStatus());
        var request = signed(now.getEpochSecond()); request.addHeader("X-Internal-User-Id", "attacker");
        var duplicate = new MockHttpServletResponse(); filter.doFilter(request, duplicate, new MockFilterChain());
        assertEquals(401, duplicate.getStatus());
    }
}

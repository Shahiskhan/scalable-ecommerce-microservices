package com.commerce.payment;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class InternalContextFilterTest {
    static final String SECRET="01234567890123456789012345678901";
    MockHttpServletRequest signed(long timestamp) throws Exception {
        var request=new MockHttpServletRequest("GET","/api/orders/123");
        String id=UUID.randomUUID().toString();
        request.addHeader("X-Internal-User-Id","alice"); request.addHeader("X-Internal-Roles","USER");
        request.addHeader("X-Internal-Timestamp",timestamp); request.addHeader("X-Internal-Request-Id",id);
        var mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        var body="GET\n/api/orders/123\nalice\nUSER\n"+timestamp+"\n"+id;
        request.addHeader("X-Internal-Signature",Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8))));
        return request;
    }
    @Test void validatesPathMethodTimestampAndHeaderUniqueness() throws Exception {
        var now=Instant.parse("2026-10-26T00:00:00Z");
        var filter=new InternalContextFilter(SECRET,java.time.Clock.fixed(now,java.time.ZoneOffset.UTC));
        assertTrue(filter.valid(signed(now.getEpochSecond())));
        assertFalse(filter.valid(signed(now.minusSeconds(40).getEpochSecond())));
        var changed=signed(now.getEpochSecond()); changed.setMethod("POST"); assertFalse(filter.valid(changed));
        var path=signed(now.getEpochSecond()); path.setRequestURI("/api/orders/456"); assertFalse(filter.valid(path));
        var duplicates=signed(now.getEpochSecond()); duplicates.addHeader("X-Internal-User-Id","mallory"); assertFalse(filter.valid(duplicates));
    }
}


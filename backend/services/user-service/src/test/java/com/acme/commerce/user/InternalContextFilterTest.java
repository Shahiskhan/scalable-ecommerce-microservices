package com.acme.commerce.user;
import com.acme.commerce.user.security.InternalContextFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
class InternalContextFilterTest {
    private static final java.time.Clock CLOCK = java.time.Clock.fixed(Instant.parse("2026-10-26T00:00:00Z"), java.time.ZoneOffset.UTC);
    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private MockHttpServletRequest signed(long timestamp) throws Exception {
        var r = new MockHttpServletRequest("GET", "/api/users/me");
        String id=UUID.randomUUID().toString();
        r.addHeader("X-Internal-User-Id","alice"); r.addHeader("X-Internal-Roles","USER");
        r.addHeader("X-Internal-Timestamp",Long.toString(timestamp)); r.addHeader("X-Internal-Request-Id",id);
        var mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        r.addHeader("X-Internal-Signature",Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(String.join("\n","GET","/api/users/me","alice","USER",Long.toString(timestamp),id).getBytes(StandardCharsets.UTF_8))));
        return r;
    }
    @Test void validContextPropagatesVerifiedIdentity() throws Exception {
        var r=signed(CLOCK.instant().getEpochSecond()); var out=new MockHttpServletResponse();
        new InternalContextFilter(SECRET, CLOCK).doFilter(r,out,(req,res)->assertThat(req.getAttribute("userId")).isEqualTo("alice"));
        assertThat(out.getStatus()).isEqualTo(200);
    }
    @Test void rejectsUnsignedAndStaleRequests() throws Exception {
        for(var request : new MockHttpServletRequest[]{new MockHttpServletRequest("GET","/api/users/me"),signed(CLOCK.instant().getEpochSecond()-60)}) {
            var out=new MockHttpServletResponse();
            new InternalContextFilter(SECRET, CLOCK).doFilter(request,out,(req,res)->fail("Untrusted request reached controller"));
            assertThat(out.getStatus()).isEqualTo(401);
        }
    }
    @Test void signatureBindsMethodAndPath() throws Exception {
        var r=signed(CLOCK.instant().getEpochSecond()); r.setMethod("PUT"); var out=new MockHttpServletResponse();
        new InternalContextFilter(SECRET, CLOCK).doFilter(r,out,(req,res)->fail("Tampered method accepted"));
        assertThat(out.getStatus()).isEqualTo(401);
    }
}

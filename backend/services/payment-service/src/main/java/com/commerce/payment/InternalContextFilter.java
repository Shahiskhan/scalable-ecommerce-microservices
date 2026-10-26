package com.commerce.payment;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Component
public class InternalContextFilter extends OncePerRequestFilter {
    private final byte[] secret;
    private final java.time.Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public InternalContextFilter(@Value("${commerce.internal-secret}") String secret) {
        this(secret,java.time.Clock.systemUTC());
    }
    InternalContextFilter(String secret,java.time.Clock clock) {
        this.clock=clock;
        this.secret=secret.getBytes(StandardCharsets.UTF_8);
        if (this.secret.length < 32) throw new IllegalStateException("Internal context key must be at least 32 bytes");
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException, IOException {
        if (request.getRequestURI().equals("/actuator/health") || request.getRequestURI().startsWith("/actuator/health/")) {
            chain.doFilter(request,response); return;
        }
        if (!request.getRequestURI().startsWith("/api/")) { response.sendError(404); return; }
        if (!valid(request)) { response.sendError(401); return; }
        chain.doFilter(request,response);
    }
    boolean valid(HttpServletRequest request) {
        try {
            String user=single(request,"X-Internal-User-Id"), roles=single(request,"X-Internal-Roles"),
                    timestamp=single(request,"X-Internal-Timestamp"), id=single(request,"X-Internal-Request-Id"),
                    signature=single(request,"X-Internal-Signature");
            if (user.isBlank() || user.length()>128 || user.contains("\n") || user.contains("\r")
                    || !roles.matches("[A-Za-z0-9_,.-]*")) return false;
            long received=Long.parseLong(timestamp), now=clock.instant().getEpochSecond();
            if (received < now-30 || received > now+30) return false;
            UUID.fromString(id);
            String canonical=request.getMethod()+"\n"+request.getRequestURI()+"\n"+user+"\n"+roles+"\n"+timestamp+"\n"+id;
            Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret,"HmacSHA256"));
            return MessageDigest.isEqual(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)), Base64.getUrlDecoder().decode(signature));
        } catch (Exception invalid) { return false; }
    }
    private static String single(HttpServletRequest request,String name) {
        var values=Collections.list(request.getHeaders(name));
        if (values.size()!=1) throw new IllegalArgumentException("Ambiguous internal header");
        return values.getFirst();
    }
}


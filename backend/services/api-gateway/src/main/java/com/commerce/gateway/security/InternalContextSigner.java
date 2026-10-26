package com.commerce.gateway.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Signs identity context; never a replacement for private networking and internal TLS. */
public final class InternalContextSigner {
    private final byte[] secret;

    public InternalContextSigner(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("INTERNAL_HMAC_SECRET must contain at least 32 UTF-8 bytes");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String sign(String method, String path, String user, String roles, String timestamp, String requestId) {
        String canonical = String.join("\n", method, path, user, roles, timestamp, requestId);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException unavailableAlgorithm) {
            throw new IllegalStateException("HmacSHA256 unavailable", unavailableAlgorithm);
        }
    }
}

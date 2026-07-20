package com.penmate.backend.infrastructure.ratelimit;

import com.penmate.backend.application.ratelimit.port.RateLimitKeyEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

@Component
public class HmacRateLimitKeyEncoder implements RateLimitKeyEncoder {
    private static final String ALGORITHM = "HmacSHA256";
    private final byte[] secret;

    public HmacRateLimitKeyEncoder(@Value("${penmate.rate-limit.key-secret:}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("RATE_LIMIT_KEY_SECRET is required");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String encode(String namespace, String subject, boolean hashSubject) {
        String encodedSubject = hashSubject ? hmac(subject) : subject;
        return "rate-limit:" + namespace + ":" + encodedSubject;
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to create rate limit key", exception);
        }
    }
}

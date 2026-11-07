package com.example.expensetracker.auth;

import io.jsonwebtoken.security.Keys;
import java.util.Base64;
import javax.crypto.SecretKey;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long expirationSeconds) {

    private static final int MINIMUM_SECRET_BYTES = 32;
    private static final String INVALID_SECRET = "JWT secret must be Base64 encoding of at least 32 random bytes";

    public JwtProperties {
        decodeSecret(secret);
        if (expirationSeconds <= 0) {
            throw new IllegalArgumentException("JWT expiration seconds must be positive");
        }
    }

    public SecretKey signingKey() {
        SecretKey key = Keys.hmacShaKeyFor(decodeSecret(secret));
        return key;
    }

    private static byte[] decodeSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException(INVALID_SECRET);
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(INVALID_SECRET);
        }
        if (decoded.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalArgumentException(INVALID_SECRET);
        }
        return decoded;
    }

    @Override
    public String toString() {
        String description = "JwtProperties[secret=redacted, expirationSeconds=" + expirationSeconds + "]";
        return description;
    }
}

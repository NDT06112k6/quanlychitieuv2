package com.example.expensetracker.auth;

import com.example.expensetracker.user.UserRole;
import java.time.Clock;
import java.time.Duration;

public final class JwtTestFixtures {

    public static final String SECRET = "dGVzdC1vbmx5LWV4cGVuc2UtdHJhY2tlci1qd3Qta2V5LTMyaA==";
    public static final long EXPIRATION_SECONDS = 3600;
    public static final AuthPrincipal PRINCIPAL = new AuthPrincipal(1L, UserRole.USER);

    private JwtTestFixtures() {
    }

    public static JwtProperties properties() {
        JwtProperties properties = new JwtProperties(SECRET, EXPIRATION_SECONDS);
        return properties;
    }

    public static String expiredToken() {
        Clock clock = Clock.offset(Clock.systemUTC(), Duration.ofHours(-2));
        JwtService issuer = new JwtService(properties(), clock);
        String token = issuer.createToken(PRINCIPAL);
        return token;
    }
}

package com.example.expensetracker.auth;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class JwtServiceTests {

    private static final Instant NOW = Instant.parse("2026-11-08T00:00:00Z");
    @Mock
    private Clock clock;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(NOW);
        jwtService = new JwtService(JwtTestFixtures.properties(), clock);
    }

    @Test
    void verifiesSignatureRoleAndUserId() {
        when(clock.instant()).thenReturn(NOW);

        String token = jwtService.createToken(JwtTestFixtures.PRINCIPAL);

        assertThat(jwtService.validateToken(token)).isEqualTo(JwtTestFixtures.PRINCIPAL);
        assertThat(jwtService.readUserId(token)).isEqualTo(JwtTestFixtures.PRINCIPAL.userId());
        assertThat(jwtService.expiresInSeconds()).isEqualTo(JwtTestFixtures.EXPIRATION_SECONDS);
    }

    @Test
    void rejectsExpiredToken() {
        when(clock.instant()).thenReturn(NOW);
        String token = jwtService.createToken(JwtTestFixtures.PRINCIPAL);
        when(clock.instant()).thenReturn(NOW.plusSeconds(JwtTestFixtures.EXPIRATION_SECONDS + 1));

        assertThatThrownBy(() -> jwtService.validateToken(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenAtTheExpirationBoundary() {
        when(clock.instant()).thenReturn(NOW);
        String token = jwtService.createToken(JwtTestFixtures.PRINCIPAL);
        when(clock.instant()).thenReturn(NOW.plusSeconds(JwtTestFixtures.EXPIRATION_SECONDS));

        assertThatThrownBy(() -> jwtService.readUserId(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsDifferentSigningKey() {
        String otherSecret = Base64.getEncoder().encodeToString(new byte[32]);
        JwtProperties otherProperties = new JwtProperties(otherSecret, JwtTestFixtures.EXPIRATION_SECONDS);
        JwtService otherIssuer = new JwtService(otherProperties, Clock.fixed(NOW, java.time.ZoneOffset.UTC));
        String token = otherIssuer.createToken(JwtTestFixtures.PRINCIPAL);

        assertThatThrownBy(() -> jwtService.validateToken(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsModifiedPayload() {
        when(clock.instant()).thenReturn(NOW);
        String token = jwtService.createToken(JwtTestFixtures.PRINCIPAL);
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String forgedPayload = payload.replace("\"USER\"", "\"ADMIN\"");
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(forgedPayload.getBytes(StandardCharsets.UTF_8));
        String modified = parts[0] + "." + encodedPayload + "." + parts[2];

        assertThatThrownBy(() -> jwtService.validateToken(modified)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsSignedTokenWithoutExpiration() {
        String token = Jwts.builder().subject("1").claim(AuthConstants.ROLE_CLAIM, "USER")
                .signWith(JwtTestFixtures.properties().signingKey(), Jwts.SIG.HS256).compact();

        assertThatThrownBy(() -> jwtService.validateToken(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsSignedTokenWithInvalidIdentity() {
        when(clock.instant()).thenReturn(NOW);
        String token = Jwts.builder().subject("not-a-user-id").claim(AuthConstants.ROLE_CLAIM, "USER")
                .expiration(Date.from(NOW.plusSeconds(60)))
                .signWith(JwtTestFixtures.properties().signingKey(), Jwts.SIG.HS256).compact();

        assertThatThrownBy(() -> jwtService.validateToken(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsUnsecuredToken() {
        String token = Jwts.builder().subject("1").claim(AuthConstants.ROLE_CLAIM, "ADMIN").compact();

        assertThatThrownBy(() -> jwtService.validateToken(token)).isInstanceOf(JwtException.class);
    }
}

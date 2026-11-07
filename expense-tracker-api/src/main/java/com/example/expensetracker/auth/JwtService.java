package com.example.expensetracker.auth;

import com.example.expensetracker.user.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String INVALID_CLAIMS = "Invalid JWT claims";
    private final SecretKey signingKey;
    private final long expirationSeconds;
    private final Clock clock;
    private final JwtParser parser;

    public JwtService(JwtProperties properties, Clock clock) {
        this.signingKey = properties.signingKey();
        this.expirationSeconds = properties.expirationSeconds();
        this.clock = clock;
        this.parser = Jwts.parser()
                .verifyWith(signingKey)
                .clock(() -> Date.from(clock.instant()))
                .sig()
                .clear()
                .add(Jwts.SIG.HS256)
                .and()
                .build();
    }

    public String createToken(AuthPrincipal principal) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plusSeconds(expirationSeconds);
        String token = Jwts.builder()
                .subject(principal.userId().toString())
                .claim(AuthConstants.ROLE_CLAIM, principal.role().name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
        return token;
    }

    public AuthPrincipal validateToken(String token) {
        Claims claims = parser.parseSignedClaims(token).getPayload();
        if (claims.getExpiration() == null || !claims.getExpiration().toInstant().isAfter(clock.instant())) {
            throw new JwtException(INVALID_CLAIMS);
        }
        AuthPrincipal principal = readPrincipal(claims);
        return principal;
    }

    public Long readUserId(String token) {
        AuthPrincipal principal = validateToken(token);
        Long userId = principal.userId();
        return userId;
    }

    public long expiresInSeconds() {
        return expirationSeconds;
    }

    private AuthPrincipal readPrincipal(Claims claims) {
        try {
            Long userId = Long.valueOf(claims.getSubject());
            String roleClaim = claims.get(AuthConstants.ROLE_CLAIM, String.class);
            if (roleClaim == null) {
                throw new JwtException(INVALID_CLAIMS);
            }
            UserRole role = UserRole.valueOf(roleClaim);
            AuthPrincipal principal = new AuthPrincipal(userId, role);
            return principal;
        } catch (IllegalArgumentException exception) {
            throw new JwtException(INVALID_CLAIMS);
        }
    }
}

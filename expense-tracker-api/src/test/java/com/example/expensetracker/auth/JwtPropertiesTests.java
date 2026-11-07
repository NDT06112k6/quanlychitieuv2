package com.example.expensetracker.auth;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtPropertiesTests {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not base64!", "c2hvcnQ="})
    void rejectsMissingMalformedOrShortSecrets(String secret) {
        assertThatThrownBy(() -> new JwtProperties(secret, JwtTestFixtures.EXPIRATION_SECONDS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveExpiration() {
        assertThatThrownBy(() -> new JwtProperties(JwtTestFixtures.SECRET, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsExactly32DecodedBytesAndRedactsSecretFromToString() {
        String secret = Base64.getEncoder().encodeToString(new byte[32]);
        JwtProperties properties = new JwtProperties(secret, JwtTestFixtures.EXPIRATION_SECONDS);

        assertThat(properties.signingKey().getEncoded()).hasSize(32);
        assertThat(properties.toString()).doesNotContain(secret);
    }
}

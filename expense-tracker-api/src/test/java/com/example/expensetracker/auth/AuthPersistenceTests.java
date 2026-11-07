package com.example.expensetracker.auth;

import com.example.expensetracker.support.PersistenceTest;
import com.example.expensetracker.user.User;
import com.example.expensetracker.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@PersistenceTest
@ExtendWith(OutputCaptureExtension.class)
class AuthPersistenceTests {

    private static final String EMAIL = "log-privacy@example.invalid";
    private final UserRepository userRepository;

    @Autowired
    AuthPersistenceTests(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Test
    void normalizesStoredEmailAndSuppressesPiiFromDuplicateSqlErrors(CapturedOutput output) {
        String passwordHash = PasswordEncoderFactories.createDelegatingPasswordEncoder().encode("test-password");
        User user = new User(" LOG-PRIVACY@EXAMPLE.INVALID ", passwordHash, "Owner");
        userRepository.saveAndFlush(user);

        assertThat(userRepository.existsByEmail(EMAIL)).isTrue();
        assertThat(userRepository.findByEmail(EMAIL).orElseThrow().getId()).isEqualTo(user.getId());
        assertThatThrownBy(() -> userRepository.saveAndFlush(new User(EMAIL, passwordHash, "Other")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(output).doesNotContain(EMAIL, passwordHash);
    }
}

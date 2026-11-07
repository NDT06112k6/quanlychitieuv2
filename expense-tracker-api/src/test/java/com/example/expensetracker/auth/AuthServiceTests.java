package com.example.expensetracker.auth;

import com.example.expensetracker.common.exception.ErrorMessages;
import com.example.expensetracker.support.DomainFixtures;
import com.example.expensetracker.user.User;
import com.example.expensetracker.user.UserRepository;
import com.example.expensetracker.user.UserRole;
import java.sql.SQLException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {

    private static final String EMAIL = "owner@example.invalid";
    private static final String PASSWORD = "test-password";
    private static final String HASH = "{bcrypt}test-hash";
    private static final String TOKEN = "test-access-token";
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn(HASH);
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void registersNormalizedEmailWithEncodedPasswordAndUserRole() {
        RegisterRequest request = new RegisterRequest("  OWNER@EXAMPLE.INVALID  ", PASSWORD, "Owner");
        when(userRepository.saveAndFlush(any())).thenReturn(DomainFixtures.user());
        when(jwtService.createToken(JwtTestFixtures.PRINCIPAL)).thenReturn(TOKEN);
        when(jwtService.expiresInSeconds()).thenReturn(JwtTestFixtures.EXPIRATION_SECONDS);

        AuthResponse response = authService.register(request);

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).existsByEmail(EMAIL);
        verify(userRepository).saveAndFlush(savedUser.capture());
        verify(passwordEncoder).encode(PASSWORD);
        assertThat(savedUser.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(savedUser.getValue().getPasswordHash()).isEqualTo(HASH);
        assertThat(savedUser.getValue().getRole()).isEqualTo(UserRole.USER);
        assertThat(response.accessToken()).isEqualTo(TOKEN);
        assertThat(response.tokenType()).isEqualTo(AuthConstants.TOKEN_TYPE);
        assertThat(response.toString()).doesNotContain(TOKEN);
    }

    @Test
    void rejectsDuplicateEmailBeforePersisting() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest(EMAIL, PASSWORD, "Owner")))
                .isInstanceOf(EmailAlreadyExistsException.class);
        verify(userRepository, never()).saveAndFlush(any());
        verify(jwtService, never()).createToken(any());
    }

    @Test
    void convertsConcurrentEmailUniquenessFailureToConflict() {
        org.hibernate.exception.ConstraintViolationException duplicate = new org.hibernate.exception.ConstraintViolationException(
                "Duplicate", new SQLException("Duplicate", "23000", 1062), "uk_users_email");
        when(userRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("Duplicate", duplicate));

        assertThatThrownBy(() -> authService.register(new RegisterRequest(EMAIL, PASSWORD, "Owner")))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    void doesNotHideUnrelatedPersistenceFailureAsEmailConflict() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("Other constraint");
        when(userRepository.saveAndFlush(any())).thenThrow(failure);

        assertThatThrownBy(() -> authService.register(new RegisterRequest(EMAIL, PASSWORD, "Owner"))).isSameAs(failure);
    }

    @Test
    void logsInUsingNormalizedEmail() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(DomainFixtures.user()));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(jwtService.createToken(JwtTestFixtures.PRINCIPAL)).thenReturn(TOKEN);

        AuthResponse response = authService.login(new LoginRequest(" OWNER@EXAMPLE.INVALID ", PASSWORD));

        assertThat(response.accessToken()).isEqualTo(TOKEN);
        verify(userRepository).findByEmail(EMAIL);
    }

    @Test
    void rejectsWrongPasswordWithGenericMessage() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(DomainFixtures.user()));

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD)))
                .isInstanceOf(BadCredentialsException.class).hasMessage(ErrorMessages.INVALID_CREDENTIALS);
        verify(jwtService, never()).createToken(any());
    }

    @Test
    void checksDummyHashForUnknownAccountWithTheSameGenericMessage() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD)))
                .isInstanceOf(BadCredentialsException.class).hasMessage(ErrorMessages.INVALID_CREDENTIALS);
        verify(passwordEncoder).matches(PASSWORD, HASH);
        verify(jwtService, never()).createToken(any());
    }

    @Test
    void rejectsDisabledAccountWithTheSameGenericMessage() {
        User user = DomainFixtures.user();
        ReflectionTestUtils.setField(user, "enabled", false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD)))
                .isInstanceOf(BadCredentialsException.class).hasMessage(ErrorMessages.INVALID_CREDENTIALS);
        verify(jwtService, never()).createToken(any());
    }
}

package com.example.expensetracker.auth;

import com.example.expensetracker.common.exception.ErrorMessages;
import com.example.expensetracker.user.User;
import com.example.expensetracker.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Slf4j
@Transactional(readOnly = true)
public class AuthService {

    private static final String EMAIL_UNIQUE_CONSTRAINT = "uk_users_email";
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthResponse register(@Valid @NotNull RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw duplicateEmail();
        }
        String passwordHash = passwordEncoder.encode(request.password());
        User user = new User(request.email(), passwordHash, request.displayName());
        User saved = saveRegisteredUser(user);
        AuthResponse response = issueToken(saved);
        log.info("User registration completed.");
        return response;
    }

    public AuthResponse login(@Valid @NotNull LoginRequest request) {
        User user = userRepository.findByEmail(request.email()).orElse(null);
        String storedHash = dummyPasswordHash;
        if (user != null) {
            storedHash = user.getPasswordHash();
        }
        // Perform the same password work for an unknown account to reduce account enumeration through timing.
        boolean passwordMatches = passwordEncoder.matches(request.password(), storedHash);
        if (user == null || !passwordMatches || !user.isEnabled()) {
            log.warn("Login rejected for invalid credentials.");
            throw new BadCredentialsException(ErrorMessages.INVALID_CREDENTIALS);
        }
        AuthResponse response = issueToken(user);
        return response;
    }

    private User saveRegisteredUser(User user) {
        try {
            User saved = userRepository.saveAndFlush(user);
            return saved;
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateEmail(exception)) {
                throw duplicateEmail();
            }
            throw exception;
        }
    }

    private boolean isDuplicateEmail(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                String constraintName = violation.getConstraintName().toLowerCase(Locale.ROOT);
                if (constraintName.contains(EMAIL_UNIQUE_CONSTRAINT)) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }

    private EmailAlreadyExistsException duplicateEmail() {
        log.warn("Registration rejected because the email is already registered.");
        EmailAlreadyExistsException exception = new EmailAlreadyExistsException();
        return exception;
    }

    private AuthResponse issueToken(User user) {
        AuthPrincipal principal = new AuthPrincipal(user.getId(), user.getRole());
        String token = jwtService.createToken(principal);
        AuthResponse response = new AuthResponse(token, AuthConstants.TOKEN_TYPE, jwtService.expiresInSeconds());
        return response;
    }
}

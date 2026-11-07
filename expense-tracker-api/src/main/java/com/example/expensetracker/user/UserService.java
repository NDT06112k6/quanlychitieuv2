package com.example.expensetracker.user;

import com.example.expensetracker.common.util.AccessGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Slf4j
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public User requireUser(@NotNull @Positive Long userId) {
        User user = AccessGuard.requireOwned(userRepository.findByIdAndEnabledTrue(userId));
        return user;
    }

    public UserResponse getProfile(@NotNull @Positive Long userId) {
        User user = requireUser(userId);
        UserResponse response = UserResponse.from(user);
        return response;
    }

    @Transactional
    public UserResponse updateProfile(@NotNull @Positive Long userId, @Valid @NotNull UserProfileRequest request) {
        requireUser(userId);
        int affectedRows = userRepository.updateDisplayName(userId, request.displayName());
        AccessGuard.requireSuccessfulWrite(affectedRows);
        UserResponse response = getProfile(userId);
        return response;
    }

    @Transactional
    public void changePassword(@NotNull @Positive Long userId, @Valid @NotNull PasswordChangeRequest request) {
        User user = requireUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            log.warn("Rejected password change with an incorrect current password.");
            throw new AccessDeniedException("Current password is incorrect");
        }
        String encodedPassword = passwordEncoder.encode(request.newPassword());
        int affectedRows = userRepository.updatePasswordHash(userId, encodedPassword);
        AccessGuard.requireSuccessfulWrite(affectedRows);
    }
}

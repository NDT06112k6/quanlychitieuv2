package com.example.expensetracker.user;

import com.example.expensetracker.support.DomainFixtures;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static com.example.expensetracker.support.DomainFixtures.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTests {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @InjectMocks
    private UserService userService;

    @Test
    void returnsProfileWithoutPasswordHash() {
        when(userRepository.findByIdAndEnabledTrue(USER_ID)).thenReturn(Optional.of(DomainFixtures.user()));

        UserResponse profile = userService.getProfile(USER_ID);

        assertThat(profile.displayName()).isEqualTo("Owner");
        assertThat(profile.toString()).doesNotContain("test-hash");
    }

    @Test
    void deniesMissingOrDisabledUser() {
        when(userRepository.findByIdAndEnabledTrue(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(USER_ID)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void encodesChangedPasswordAndScopesUpdateToUser() {
        User user = DomainFixtures.user();
        PasswordChangeRequest request = new PasswordChangeRequest("old-password", "new-password");
        when(userRepository.findByIdAndEnabledTrue(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.encode(request.newPassword())).thenReturn("{bcrypt}new-hash");
        when(userRepository.updatePasswordHash(USER_ID, "{bcrypt}new-hash")).thenReturn(1);

        userService.changePassword(USER_ID, request);

        verify(userRepository).updatePasswordHash(USER_ID, "{bcrypt}new-hash");
        assertThat(request.toString()).doesNotContain(request.currentPassword(), request.newPassword());
    }

    @Test
    void rejectsWrongCurrentPasswordWithoutWriting() {
        User user = DomainFixtures.user();
        when(userRepository.findByIdAndEnabledTrue(USER_ID)).thenReturn(Optional.of(user));
        PasswordChangeRequest request = new PasswordChangeRequest("incorrect-password", "new-password");

        assertThatThrownBy(() -> userService.changePassword(USER_ID, request))
                .isInstanceOf(AccessDeniedException.class);
        verify(userRepository, never()).updatePasswordHash(any(), any());
    }

    @Test
    void failsWhenProfileUpdateLosesItsOwner() {
        when(userRepository.findByIdAndEnabledTrue(USER_ID)).thenReturn(Optional.of(DomainFixtures.user()));
        when(userRepository.updateDisplayName(USER_ID, "Changed")).thenReturn(0);

        assertThatThrownBy(() -> userService.updateProfile(USER_ID, new UserProfileRequest("Changed")))
                .isInstanceOf(AccessDeniedException.class);
    }
}

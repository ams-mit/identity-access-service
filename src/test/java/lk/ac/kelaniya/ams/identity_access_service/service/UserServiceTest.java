package lk.ac.kelaniya.ams.identity_access_service.service;

import lk.ac.kelaniya.ams.identity_access_service.dto.request.ChangePasswordRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.UserSummaryResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lk.ac.kelaniya.ams.identity_access_service.entity.Role;
import lk.ac.kelaniya.ams.identity_access_service.entity.User;
import lk.ac.kelaniya.ams.identity_access_service.exception.AccountStatusException;
import lk.ac.kelaniya.ams.identity_access_service.exception.InvalidCredentialsException;
import lk.ac.kelaniya.ams.identity_access_service.exception.PasswordMismatchException;
import lk.ac.kelaniya.ams.identity_access_service.exception.SamePasswordException;
import lk.ac.kelaniya.ams.identity_access_service.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import lk.ac.kelaniya.ams.identity_access_service.entity.AuditEventType;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("getCurrentUser returns user profile when user exists and is active")
    void testGetCurrentUser_success() {
        UUID userId = UUID.randomUUID();
        Role roleResident = Role.builder().id(UUID.randomUUID()).name("ROLE_RESIDENT").build();

        User user = User.builder()
                .id(userId)
                .email("john.doe@example.com")
                .firstName("John")
                .lastName("Doe")
                .accountStatus(AccountStatus.ACTIVE)
                .requestedRole("OWNER")
                .build();
        user.addRole(roleResident);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        UserSummaryResponse response = userService.getCurrentUser(userId);

        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getEmail()).isEqualTo("john.doe@example.com");
        assertThat(response.getFirstName()).isEqualTo("John");
        assertThat(response.getLastName()).isEqualTo("Doe");
        assertThat(response.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(response.getRoles()).containsExactly("ROLE_RESIDENT");
        assertThat(response.getRequestedRole()).isEqualTo("OWNER");
    }

    @Test
    @DisplayName("getCurrentUser reflects fresh database changes")
    void testGetCurrentUser_reflectsDbChanges() {
        UUID userId = UUID.randomUUID();
        User updatedUser = User.builder()
                .id(userId)
                .email("john.updated@example.com")
                .firstName("Johnny")
                .lastName("Doeman")
                .accountStatus(AccountStatus.ACTIVE)
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(updatedUser));

        UserSummaryResponse response = userService.getCurrentUser(userId);

        assertThat(response.getEmail()).isEqualTo("john.updated@example.com");
        assertThat(response.getFirstName()).isEqualTo("Johnny");
        assertThat(response.getLastName()).isEqualTo("Doeman");
    }

    @Test
    @DisplayName("getCurrentUser throws InvalidCredentialsException when user not found in database")
    void testGetCurrentUser_userNotFound_throwsException() {
        UUID userId = UUID.randomUUID();
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getCurrentUser(userId))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("User not found or no longer exists");
    }

    @Test
    @DisplayName("getCurrentUser throws AccountStatusException when user is DEACTIVATED")
    void testGetCurrentUser_deactivated_throwsException() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("deactivated@example.com")
                .firstName("Deactivated")
                .lastName("User")
                .accountStatus(AccountStatus.DEACTIVATED)
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.getCurrentUser(userId))
                .isInstanceOf(AccountStatusException.class)
                .hasMessageContaining("Account has been deactivated")
                .satisfies(ex -> assertThat(((AccountStatusException) ex).getErrorCode()).isEqualTo("ACCOUNT_DEACTIVATED"));
    }

    @Test
    @DisplayName("getCurrentUser rejects previously active user if database status has been updated to DEACTIVATED")
    void testGetCurrentUser_accountDeactivatedAfterTokenIssued_rejectedWithAccountStatusException() {
        UUID userId = UUID.randomUUID();
        // User was originally ACTIVE when token was issued, but database status was subsequently set to DEACTIVATED
        User deactivatedInDbUser = User.builder()
                .id(userId)
                .email("originally.active@example.com")
                .firstName("Original")
                .lastName("User")
                .accountStatus(AccountStatus.DEACTIVATED)
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(deactivatedInDbUser));

        assertThatThrownBy(() -> userService.getCurrentUser(userId))
                .isInstanceOf(AccountStatusException.class)
                .hasMessageContaining("Account has been deactivated")
                .satisfies(ex -> assertThat(((AccountStatusException) ex).getErrorCode()).isEqualTo("ACCOUNT_DEACTIVATED"));
    }

    @Test
    @DisplayName("changePassword: correct current password succeeds, updates hash, clears mustChangePassword, leaves lockout and status untouched")
    void testChangePassword_success_updatesHashAndClearsMustChangePassword() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("user@example.com")
                .passwordHash("$2a$10$existingHashOldPassword")
                .accountStatus(AccountStatus.ACTIVE)
                .mustChangePassword(true)
                .failedAttemptCount(2)
                .build();

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("OldP@ssword123")
                .newPassword("NewP@ssword456")
                .confirmNewPassword("NewP@ssword456")
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("OldP@ssword123", "$2a$10$existingHashOldPassword")).willReturn(true);
        given(passwordEncoder.matches("NewP@ssword456", "$2a$10$existingHashOldPassword")).willReturn(false);
        given(passwordEncoder.encode("NewP@ssword456")).willReturn("$2a$10$brandNewEncodedPasswordHash");
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        userService.changePassword(userId, request);

        assertThat(user.getPasswordHash()).isEqualTo("$2a$10$brandNewEncodedPasswordHash");
        assertThat(user.isMustChangePassword()).isFalse();
        assertThat(user.getFailedAttemptCount()).isEqualTo(2);
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);

        verify(userRepository).save(user);
        verify(auditService).record(
                AuditEventType.PASSWORD_CHANGED,
                userId,
                userId,
                null,
                null,
                "User self-service password change"
        );
    }

    @Test
    @DisplayName("changePassword: wrong current password throws InvalidCredentialsException with generic message")
    void testChangePassword_wrongCurrentPassword_throwsInvalidCredentialsException() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("user@example.com")
                .passwordHash("$2a$10$existingHashOldPassword")
                .accountStatus(AccountStatus.ACTIVE)
                .mustChangePassword(true)
                .build();

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("IncorrectPass123")
                .newPassword("NewP@ssword456")
                .confirmNewPassword("NewP@ssword456")
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("IncorrectPass123", "$2a$10$existingHashOldPassword")).willReturn(false);

        assertThatThrownBy(() -> userService.changePassword(userId, request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Current password is incorrect.");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("changePassword: mismatched new and confirm passwords throws PasswordMismatchException")
    void testChangePassword_mismatchedNewConfirmPassword_throwsPasswordMismatchException() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("user@example.com")
                .passwordHash("$2a$10$existingHashOldPassword")
                .accountStatus(AccountStatus.ACTIVE)
                .mustChangePassword(true)
                .build();

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("OldP@ssword123")
                .newPassword("NewP@ssword456")
                .confirmNewPassword("DifferentPass789")
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("OldP@ssword123", "$2a$10$existingHashOldPassword")).willReturn(true);

        assertThatThrownBy(() -> userService.changePassword(userId, request))
                .isInstanceOf(PasswordMismatchException.class)
                .hasMessageContaining("do not match");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("changePassword: new password same as current password throws SamePasswordException")
    void testChangePassword_newPasswordSameAsCurrent_throwsSamePasswordException() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("user@example.com")
                .passwordHash("$2a$10$existingHashOldPassword")
                .accountStatus(AccountStatus.ACTIVE)
                .mustChangePassword(true)
                .build();

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("OldP@ssword123")
                .newPassword("OldP@ssword123")
                .confirmNewPassword("OldP@ssword123")
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("OldP@ssword123", "$2a$10$existingHashOldPassword")).willReturn(true);

        assertThatThrownBy(() -> userService.changePassword(userId, request))
                .isInstanceOf(SamePasswordException.class)
                .hasMessage("New password must differ from current password");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("changePassword: user not found throws InvalidCredentialsException")
    void testChangePassword_userNotFound_throwsInvalidCredentialsException() {
        UUID userId = UUID.randomUUID();
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("OldP@ssword123")
                .newPassword("NewP@ssword456")
                .confirmNewPassword("NewP@ssword456")
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.changePassword(userId, request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Current password is incorrect.");

        verify(userRepository, never()).save(any());
    }
}

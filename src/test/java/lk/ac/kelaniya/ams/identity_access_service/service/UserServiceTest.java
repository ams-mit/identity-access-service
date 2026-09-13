package lk.ac.kelaniya.ams.identity_access_service.service;

import lk.ac.kelaniya.ams.identity_access_service.dto.response.UserSummaryResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lk.ac.kelaniya.ams.identity_access_service.entity.Role;
import lk.ac.kelaniya.ams.identity_access_service.entity.User;
import lk.ac.kelaniya.ams.identity_access_service.exception.AccountStatusException;
import lk.ac.kelaniya.ams.identity_access_service.exception.InvalidCredentialsException;
import lk.ac.kelaniya.ams.identity_access_service.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

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
}

package lk.ac.kelaniya.ams.identity_access_service.service;

import lk.ac.kelaniya.ams.identity_access_service.dto.response.InternalUserResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.UpdateUserEmailResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lk.ac.kelaniya.ams.identity_access_service.entity.AuditEventType;
import lk.ac.kelaniya.ams.identity_access_service.entity.Role;
import lk.ac.kelaniya.ams.identity_access_service.entity.User;
import lk.ac.kelaniya.ams.identity_access_service.entity.UserRole;
import lk.ac.kelaniya.ams.identity_access_service.exception.DuplicateEmailException;
import lk.ac.kelaniya.ams.identity_access_service.exception.UserNotFoundException;
import lk.ac.kelaniya.ams.identity_access_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InternalUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private InternalUserService internalUserService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        Role role = Role.builder()
                .name("TENANT_RESIDENT")
                .description("Tenant Resident")
                .build();
        user = User.builder()
                .id(userId)
                .email("old.email@ams.lk")
                .username("old.email@ams.lk")
                .accountStatus(AccountStatus.ACTIVE)
                .build();
        UserRole userRole = new UserRole(user, role);
        user.setUserRoles(Set.of(userRole));
    }

    @Test
    @DisplayName("getUserForValidation returns minimal user authorization details")
    void testGetUserForValidation_success() {
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        InternalUserResponse response = internalUserService.getUserForValidation(userId);

        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(response.getRoles()).containsExactly("TENANT_RESIDENT");
    }

    @Test
    @DisplayName("getUserForValidation throws UserNotFoundException when user is missing")
    void testGetUserForValidation_notFound() {
        UUID nonExistentId = UUID.randomUUID();
        given(userRepository.findById(nonExistentId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> internalUserService.getUserForValidation(nonExistentId))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining(nonExistentId.toString());
    }

    @Test
    @DisplayName("updateUserEmail successfully updates email, username, and records EMAIL_CHANGED audit event")
    void testUpdateUserEmail_success() {
        String newEmail = "new.email@ams.lk";
        String callerService = "resident-management-service";

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(userRepository.findByEmail(newEmail)).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        UpdateUserEmailResponse response = internalUserService.updateUserEmail(userId, newEmail, callerService);

        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getEmail()).isEqualTo(newEmail);
        assertThat(user.getEmail()).isEqualTo(newEmail);
        assertThat(user.getUsername()).isEqualTo(newEmail);

        verify(auditService).record(
                eq(AuditEventType.EMAIL_CHANGED),
                eq(userId),
                eq(null),
                eq("old.email@ams.lk"),
                eq(newEmail),
                eq("Updated by caller service: resident-management-service")
        );
    }

    @Test
    @DisplayName("updateUserEmail throws UserNotFoundException when target user is not found")
    void testUpdateUserEmail_userNotFound() {
        UUID nonExistentId = UUID.randomUUID();
        given(userRepository.findById(nonExistentId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> internalUserService.updateUserEmail(nonExistentId, "test@ams.lk", "resident-management-service"))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining(nonExistentId.toString());
    }

    @Test
    @DisplayName("updateUserEmail throws DuplicateEmailException when email is already taken by another user")
    void testUpdateUserEmail_duplicateEmail() {
        String conflictingEmail = "conflict@ams.lk";
        User anotherUser = User.builder()
                .id(UUID.randomUUID())
                .email(conflictingEmail)
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(userRepository.findByEmail(conflictingEmail)).willReturn(Optional.of(anotherUser));

        assertThatThrownBy(() -> internalUserService.updateUserEmail(userId, conflictingEmail, "resident-management-service"))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessageContaining(conflictingEmail);
    }
}

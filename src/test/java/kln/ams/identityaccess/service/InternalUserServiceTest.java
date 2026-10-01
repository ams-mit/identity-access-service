package kln.ams.identityaccess.service;

import kln.ams.identityaccess.dto.internal.UserStatusResponse;
import kln.ams.identityaccess.dto.internal.UserValidationResponse;
import kln.ams.identityaccess.entity.AccountStatus;
import kln.ams.identityaccess.entity.Role;
import kln.ams.identityaccess.entity.User;
import kln.ams.identityaccess.exception.UserNotFoundException;
import kln.ams.identityaccess.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private InternalUserService internalUserService;

    private User testUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        Role tenantRole = Role.builder()
                .id(UUID.randomUUID())
                .name("TENANT_RESIDENT")
                .build();

        testUser = User.builder()
                .id(userId)
                .username("resident@example.com")
                .accountStatus(AccountStatus.ACTIVE)
                .userRoles(new HashSet<>())
                .build();
        testUser.addRole(tenantRole);
    }

    @Test
    void iamInt001_validateUser_Success_WithoutRequiredRole() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        UserValidationResponse response = internalUserService.validateUser(userId, null);

        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.isExists()).isTrue();
        assertThat(response.isActive()).isTrue();
        assertThat(response.getRoles()).contains("TENANT_RESIDENT");
        assertThat(response.getRoleMatches()).isNull();
    }

    @Test
    void iamInt001_validateUser_Success_WithMatchingRole() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        UserValidationResponse response = internalUserService.validateUser(userId, "TENANT_RESIDENT");

        assertThat(response.isActive()).isTrue();
        assertThat(response.getRoleMatches()).isTrue();
    }

    @Test
    void iamInt001_validateUser_RoleMismatch_ActiveIsFalse() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        UserValidationResponse response = internalUserService.validateUser(userId, "OWNER");

        assertThat(response.isExists()).isTrue();
        assertThat(response.isActive()).isFalse();
        assertThat(response.getRoleMatches()).isFalse();
    }

    @Test
    void validateUser_NotFound_ThrowsException() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> internalUserService.validateUser(userId, null))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void getUserStatus_Success() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        UserStatusResponse response = internalUserService.getUserStatus(userId);

        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.isActive()).isTrue();
    }
}

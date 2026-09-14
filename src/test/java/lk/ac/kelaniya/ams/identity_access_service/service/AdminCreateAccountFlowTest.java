package lk.ac.kelaniya.ams.identity_access_service.service;

import lk.ac.kelaniya.ams.identity_access_service.dto.request.AdminCreateUserRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.request.LoginRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.AdminCreateUserResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.AdminUserDetailResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.LoginResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lk.ac.kelaniya.ams.identity_access_service.entity.Role;
import lk.ac.kelaniya.ams.identity_access_service.entity.User;
import lk.ac.kelaniya.ams.identity_access_service.repository.RoleRepository;
import lk.ac.kelaniya.ams.identity_access_service.repository.UserRepository;
import lk.ac.kelaniya.ams.identity_access_service.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * End-to-end lifecycle flow test for admin-created user accounts (IAM-05).
 * Validates account creation, immediate active login with mustChangePassword=true,
 * zero initial granted roles, subsequent role assignment via IAM-07, and credential security.
 */
@ExtendWith(MockitoExtension.class)
class AdminCreateAccountFlowTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AdminUserService adminUserService;
    private AuthService authService;

    private UUID adminId;
    private UUID userId;
    private Role technicianRole;

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserService(userRepository, roleRepository, passwordEncoder);
        authService = new AuthService(userRepository, passwordEncoder, jwtService);

        adminId = UUID.randomUUID();
        userId = UUID.randomUUID();

        technicianRole = Role.builder()
                .id(UUID.randomUUID())
                .name("TECHNICIAN")
                .description("Technician Role")
                .build();
    }

    @Test
    @DisplayName("Complete admin-creation lifecycle: create -> immediate login with mustChangePassword -> zero initial roles -> role assignment")
    void testAdminCreateAccount_fullLifecycle() {
        // Step 1: Admin provisions the new user account
        AdminCreateUserRequest createRequest = AdminCreateUserRequest.builder()
                .firstName("Robert")
                .lastName("Taylor")
                .email("robert.taylor@ams.lk")
                .phone("+94711223344")
                .temporaryPassword("TempPass2026")
                .build();

        given(userRepository.existsByEmail("robert.taylor@ams.lk")).willReturn(false);
        given(passwordEncoder.encode("TempPass2026")).willReturn("$2a$10$hashedTempPassword");

        User persistedUser = User.builder()
                .id(userId)
                .email("robert.taylor@ams.lk")
                .username("robert.taylor@ams.lk")
                .passwordHash("$2a$10$hashedTempPassword")
                .firstName("Robert")
                .lastName("Taylor")
                .phone("+94711223344")
                .accountStatus(AccountStatus.ACTIVE)
                .mustChangePassword(true)
                .requestedRole(null)
                .failedAttemptCount(0)
                .userRoles(new HashSet<>())
                .build();

        given(userRepository.save(any(User.class))).willReturn(persistedUser);

        AdminCreateUserResponse createResponse = adminUserService.createUser(createRequest);

        // Verify creation output
        assertThat(createResponse.getUserId()).isEqualTo(userId);
        assertThat(createResponse.getEmail()).isEqualTo("robert.taylor@ams.lk");
        assertThat(createResponse.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(createResponse.isMustChangePassword()).isTrue();

        // Verify saved user in repository
        ArgumentCaptor<User> saveCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saveCaptor.capture());
        User createdInDb = saveCaptor.getValue();
        assertThat(createdInDb.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(createdInDb.isMustChangePassword()).isTrue();
        assertThat(createdInDb.getRequestedRole()).isNull();
        assertThat(createdInDb.getPasswordHash()).isEqualTo("$2a$10$hashedTempPassword");
        assertThat(createdInDb.getUserRoles()).isEmpty();

        // Step 2: User logs in immediately with the temporary password
        LoginRequest loginRequest = LoginRequest.builder()
                .email("robert.taylor@ams.lk")
                .password("TempPass2026")
                .build();

        given(userRepository.findByEmail("robert.taylor@ams.lk")).willReturn(Optional.of(persistedUser));
        given(passwordEncoder.matches("TempPass2026", "$2a$10$hashedTempPassword")).willReturn(true);
        given(jwtService.generateToken(eq(userId), eq("robert.taylor@ams.lk"), eq(List.of()))).willReturn("jwt.token.robert");
        given(jwtService.getExpirationSeconds()).willReturn(1800L);

        LoginResponse loginResponse = authService.login(loginRequest);

        // Confirm immediate login succeeds without PENDING_VERIFICATION block
        assertThat(loginResponse.getAccessToken()).isEqualTo("jwt.token.robert");
        // Confirm mustChangePassword is true to notify frontend to force password change
        assertThat(loginResponse.isMustChangePassword()).isTrue();
        assertThat(loginResponse.getUser().isMustChangePassword()).isTrue();
        // Confirm account initially has zero granted roles
        assertThat(loginResponse.getUser().getRoles()).isEmpty();

        // Step 3: Admin assigns a staff role separately via IAM-07 (AdminUserService.assignRole)
        given(userRepository.findById(userId)).willReturn(Optional.of(persistedUser));
        given(roleRepository.findByName("TECHNICIAN")).willReturn(Optional.of(technicianRole));

        AdminUserDetailResponse afterRoleAssignment = adminUserService.assignRole(userId, "TECHNICIAN", adminId);

        // Confirm granted role is now active
        assertThat(afterRoleAssignment.getRoles()).containsExactly("TECHNICIAN");
        assertThat(afterRoleAssignment.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(afterRoleAssignment.isMustChangePassword()).isTrue();
    }
}

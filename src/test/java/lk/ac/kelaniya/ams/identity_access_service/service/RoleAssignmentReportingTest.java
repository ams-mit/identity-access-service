package lk.ac.kelaniya.ams.identity_access_service.service;

import lk.ac.kelaniya.ams.identity_access_service.dto.response.AdminUserDetailResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.UserSummaryResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lk.ac.kelaniya.ams.identity_access_service.entity.Role;
import lk.ac.kelaniya.ams.identity_access_service.entity.User;
import lk.ac.kelaniya.ams.identity_access_service.repository.RoleRepository;
import lk.ac.kelaniya.ams.identity_access_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * End-to-end service test verifying that granting and removing roles via AdminUserService
 * directly updates what is reported by both the administrative user detail query (/api/v1/users/{userId})
 * and the user profile self-inspection query (/api/v1/users/me).
 */
@ExtendWith(MockitoExtension.class)
class RoleAssignmentReportingTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminUserService adminUserService;
    private UserService userService;

    private User targetUser;
    private UUID userId;
    private UUID adminId;

    private Role financeRole;
    private Role maintenanceRole;

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserService(userRepository, roleRepository, passwordEncoder);
        userService = new UserService(userRepository);

        userId = UUID.randomUUID();
        adminId = UUID.randomUUID();

        targetUser = User.builder()
                .id(userId)
                .username("john_staff")
                .email("john.staff@ams.lk")
                .firstName("John")
                .lastName("Staff")
                .accountStatus(AccountStatus.ACTIVE)
                .requestedRole("TENANT_RESIDENT")
                .userRoles(new HashSet<>())
                .build();

        financeRole = Role.builder()
                .id(UUID.randomUUID())
                .name("FINANCE_OFFICER")
                .description("Finance Officer")
                .build();

        maintenanceRole = Role.builder()
                .id(UUID.randomUUID())
                .name("MAINTENANCE_COORDINATOR")
                .description("Maintenance Coordinator")
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(targetUser));
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Confirm granting and removing roles alters what /users/me and /api/v1/users/{userId} report afterward")
    void testRoleGrantAndRemoval_altersUserReporting() {
        // Step 1: Initial state - user has no granted roles
        AdminUserDetailResponse initialAdminView = adminUserService.getUserById(userId);
        UserSummaryResponse initialMeView = userService.getCurrentUser(userId);

        assertThat(initialAdminView.getRoles()).isEmpty();
        assertThat(initialMeView.getRoles()).isEmpty();
        assertThat(initialAdminView.getRequestedRole()).isEqualTo("TENANT_RESIDENT");
        assertThat(initialMeView.getRequestedRole()).isEqualTo("TENANT_RESIDENT");

        // Step 2: Grant FINANCE_OFFICER (different from requestedRole TENANT_RESIDENT)
        given(roleRepository.findByName("FINANCE_OFFICER")).willReturn(Optional.of(financeRole));
        adminUserService.assignRole(userId, "FINANCE_OFFICER", adminId);

        // Verify fresh views from both /api/v1/users/{userId} and /users/me
        AdminUserDetailResponse afterFirstGrantAdminView = adminUserService.getUserById(userId);
        UserSummaryResponse afterFirstGrantMeView = userService.getCurrentUser(userId);

        assertThat(afterFirstGrantAdminView.getRoles()).containsExactly("FINANCE_OFFICER");
        assertThat(afterFirstGrantMeView.getRoles()).containsExactly("FINANCE_OFFICER");

        // Step 3: Grant second role MAINTENANCE_COORDINATOR simultaneously
        given(roleRepository.findByName("MAINTENANCE_COORDINATOR")).willReturn(Optional.of(maintenanceRole));
        adminUserService.assignRole(userId, "MAINTENANCE_COORDINATOR", adminId);

        // Verify both views report BOTH roles simultaneously
        AdminUserDetailResponse afterSecondGrantAdminView = adminUserService.getUserById(userId);
        UserSummaryResponse afterSecondGrantMeView = userService.getCurrentUser(userId);

        assertThat(afterSecondGrantAdminView.getRoles()).containsExactlyInAnyOrder("FINANCE_OFFICER", "MAINTENANCE_COORDINATOR");
        assertThat(afterSecondGrantMeView.getRoles()).containsExactlyInAnyOrder("FINANCE_OFFICER", "MAINTENANCE_COORDINATOR");

        // Step 4: Remove FINANCE_OFFICER role
        adminUserService.removeRole(userId, "FINANCE_OFFICER", adminId);

        // Verify both views report only the remaining MAINTENANCE_COORDINATOR role
        AdminUserDetailResponse afterRemovalAdminView = adminUserService.getUserById(userId);
        UserSummaryResponse afterRemovalMeView = userService.getCurrentUser(userId);

        assertThat(afterRemovalAdminView.getRoles()).containsExactly("MAINTENANCE_COORDINATOR");
        assertThat(afterRemovalMeView.getRoles()).containsExactly("MAINTENANCE_COORDINATOR");
    }
}

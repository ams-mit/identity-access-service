package lk.ac.kelaniya.ams.identity_access_service.service;

import lk.ac.kelaniya.ams.identity_access_service.dto.request.AdminCreateUserRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.AdminCreateUserResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.AdminUserDetailResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.AdminUserSummaryResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lk.ac.kelaniya.ams.identity_access_service.entity.Role;
import lk.ac.kelaniya.ams.identity_access_service.entity.User;
import lk.ac.kelaniya.ams.identity_access_service.entity.UserRole;
import lk.ac.kelaniya.ams.identity_access_service.exception.DuplicateEmailException;
import lk.ac.kelaniya.ams.identity_access_service.exception.InvalidRoleException;
import lk.ac.kelaniya.ams.identity_access_service.exception.RoleAlreadyAssignedException;
import lk.ac.kelaniya.ams.identity_access_service.exception.RoleNotAssignedException;
import lk.ac.kelaniya.ams.identity_access_service.exception.SelfRoleAssignmentException;
import lk.ac.kelaniya.ams.identity_access_service.exception.UserNotFoundException;
import lk.ac.kelaniya.ams.identity_access_service.repository.RoleRepository;
import lk.ac.kelaniya.ams.identity_access_service.repository.UserRepository;
import lk.ac.kelaniya.ams.identity_access_service.entity.AuditEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private AdminUserService adminUserService;

    @Test
    @DisplayName("searchUsers passes specification and pageable to repository and maps to AdminUserSummaryResponse")
    void testSearchUsers_withFilters_returnsMappedPage() {
        UUID userId = UUID.randomUUID();
        Role role = Role.builder().id(UUID.randomUUID()).name("OWNER").build();
        User user = User.builder()
                .id(userId)
                .username("john_doe")
                .email("john.doe@example.com")
                .firstName("John")
                .lastName("Doe")
                .accountStatus(AccountStatus.PENDING_VERIFICATION)
                .requestedRole("OWNER")
                .userRoles(new HashSet<>())
                .build();
        user.addRole(role);

        Pageable pageable = PageRequest.of(0, 10);
        Page<User> mockPage = new PageImpl<>(List.of(user), pageable, 1);

        given(userRepository.findAll(any(Specification.class), eq(pageable))).willReturn(mockPage);

        Page<AdminUserSummaryResponse> result = adminUserService.searchUsers("john", AccountStatus.PENDING_VERIFICATION, "OWNER", pageable);

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
        AdminUserSummaryResponse summary = result.getContent().get(0);
        assertThat(summary.getUserId()).isEqualTo(userId);
        assertThat(summary.getEmail()).isEqualTo("john.doe@example.com");
        assertThat(summary.getFullName()).isEqualTo("John Doe");
        assertThat(summary.getFirstName()).isEqualTo("John");
        assertThat(summary.getLastName()).isEqualTo("Doe");
        assertThat(summary.getAccountStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(summary.getRequestedRole()).isEqualTo("OWNER");
        assertThat(summary.getRoles()).containsExactly("OWNER");
        assertThat(summary.getGrantedRoles()).containsExactly("OWNER");
    }

    @Test
    @DisplayName("searchUsers handles null name fields gracefully in fullName computation")
    void testSearchUsers_handlesNullNamesGracefully() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .username("noname")
                .email("noname@example.com")
                .accountStatus(AccountStatus.ACTIVE)
                .userRoles(new HashSet<>())
                .build();

        Pageable pageable = PageRequest.of(0, 5);
        Page<User> mockPage = new PageImpl<>(List.of(user), pageable, 1);

        given(userRepository.findAll(any(Specification.class), eq(pageable))).willReturn(mockPage);

        Page<AdminUserSummaryResponse> result = adminUserService.searchUsers(null, null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        AdminUserSummaryResponse summary = result.getContent().get(0);
        assertThat(summary.getFullName()).isNull();
        assertThat(summary.getFirstName()).isNull();
        assertThat(summary.getLastName()).isNull();
        assertThat(summary.getRoles()).isEmpty();
    }

    @Test
    @DisplayName("getUserById returns full AdminUserDetailResponse when user exists")
    void testGetUserById_existingUser_returnsDetailResponse() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        Role role = Role.builder().id(UUID.randomUUID()).name("TECHNICIAN").build();
        User user = User.builder()
                .id(userId)
                .username("tech_bob")
                .email("tech.bob@example.com")
                .firstName("Bob")
                .lastName("Builder")
                .phone("+94770000001")
                .accountStatus(AccountStatus.ACTIVE)
                .requestedRole("TECHNICIAN")
                .failedAttemptCount(2)
                .lockedUntil(null)
                .createdAt(now.minusSeconds(3600))
                .updatedAt(now)
                .userRoles(new HashSet<>())
                .build();
        user.addRole(role);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        AdminUserDetailResponse detail = adminUserService.getUserById(userId);

        assertThat(detail).isNotNull();
        assertThat(detail.getUserId()).isEqualTo(userId);
        assertThat(detail.getUsername()).isEqualTo("tech_bob");
        assertThat(detail.getEmail()).isEqualTo("tech.bob@example.com");
        assertThat(detail.getFullName()).isEqualTo("Bob Builder");
        assertThat(detail.getFirstName()).isEqualTo("Bob");
        assertThat(detail.getLastName()).isEqualTo("Builder");
        assertThat(detail.getPhone()).isEqualTo("+94770000001");
        assertThat(detail.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(detail.getRequestedRole()).isEqualTo("TECHNICIAN");
        assertThat(detail.getRoles()).containsExactly("TECHNICIAN");
        assertThat(detail.getGrantedRoles()).containsExactly("TECHNICIAN");
        assertThat(detail.getFailedAttemptCount()).isEqualTo(2);
        assertThat(detail.isAccountLocked()).isFalse();
        assertThat(detail.getCreatedAt()).isEqualTo(user.getCreatedAt());
        assertThat(detail.getUpdatedAt()).isEqualTo(user.getUpdatedAt());
    }

    @Test
    @DisplayName("getUserById throws UserNotFoundException when user does not exist")
    void testGetUserById_nonExistingUser_throwsUserNotFoundException() {
        UUID missingId = UUID.randomUUID();
        given(userRepository.findById(missingId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.getUserById(missingId))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining(missingId.toString());
    }

    // =========================================================================
    // updateAccountStatus Tests
    // =========================================================================

    @Test
    @DisplayName("updateAccountStatus: PENDING_VERIFICATION -> ACTIVE succeeds without reason and preserves roles")
    void testUpdateStatus_pendingToActive_success() {
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Role role = Role.builder().id(UUID.randomUUID()).name("OWNER").build();
        User user = User.builder()
                .id(userId)
                .email("user@ams.lk")
                .accountStatus(AccountStatus.PENDING_VERIFICATION)
                .requestedRole("OWNER")
                .userRoles(new HashSet<>())
                .build();
        user.addRole(role);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        lk.ac.kelaniya.ams.identity_access_service.dto.request.UpdateAccountStatusRequest request =
                lk.ac.kelaniya.ams.identity_access_service.dto.request.UpdateAccountStatusRequest.builder()
                        .status(AccountStatus.ACTIVE)
                        .reason(null)
                        .build();

        AdminUserDetailResponse result = adminUserService.updateAccountStatus(userId, request, adminId);

        assertThat(result.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(result.getRoles()).containsExactly("OWNER");
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(userRepository).save(user);
        verify(auditService).record(
                AuditEventType.ACCOUNT_STATUS_CHANGED,
                userId,
                adminId,
                "PENDING_VERIFICATION",
                "ACTIVE",
                null
        );
    }

    @Test
    @DisplayName("updateAccountStatus: PENDING_VERIFICATION -> REJECTED succeeds with reason")
    void testUpdateStatus_pendingToRejected_withReason_success() {
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("fraud@ams.lk")
                .accountStatus(AccountStatus.PENDING_VERIFICATION)
                .requestedRole("TENANT_RESIDENT")
                .userRoles(new HashSet<>())
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        lk.ac.kelaniya.ams.identity_access_service.dto.request.UpdateAccountStatusRequest request =
                lk.ac.kelaniya.ams.identity_access_service.dto.request.UpdateAccountStatusRequest.builder()
                        .status(AccountStatus.REJECTED)
                        .reason("Fraudulent identity documents submitted")
                        .build();

        AdminUserDetailResponse result = adminUserService.updateAccountStatus(userId, request, adminId);

        assertThat(result.getAccountStatus()).isEqualTo(AccountStatus.REJECTED);
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.REJECTED);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("updateAccountStatus: ACTIVE -> SUSPENDED succeeds with reason")
    void testUpdateStatus_activeToSuspended_withReason_success() {
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("tenant@ams.lk")
                .accountStatus(AccountStatus.ACTIVE)
                .userRoles(new HashSet<>())
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        lk.ac.kelaniya.ams.identity_access_service.dto.request.UpdateAccountStatusRequest request =
                lk.ac.kelaniya.ams.identity_access_service.dto.request.UpdateAccountStatusRequest.builder()
                        .status(AccountStatus.SUSPENDED)
                        .reason("Policy violation under investigation")
                        .build();

        AdminUserDetailResponse result = adminUserService.updateAccountStatus(userId, request, adminId);

        assertThat(result.getAccountStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.SUSPENDED);
    }

    @Test
    @DisplayName("updateAccountStatus: SUSPENDED -> ACTIVE succeeds (reactivation)")
    void testUpdateStatus_suspendedToActive_success() {
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("tenant@ams.lk")
                .accountStatus(AccountStatus.SUSPENDED)
                .userRoles(new HashSet<>())
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        lk.ac.kelaniya.ams.identity_access_service.dto.request.UpdateAccountStatusRequest request =
                lk.ac.kelaniya.ams.identity_access_service.dto.request.UpdateAccountStatusRequest.builder()
                        .status(AccountStatus.ACTIVE)
                        .build();

        AdminUserDetailResponse result = adminUserService.updateAccountStatus(userId, request, adminId);

        assertThat(result.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("updateAccountStatus: ACTIVE -> DEACTIVATED succeeds with reason")
    void testUpdateStatus_activeToDeactivated_withReason_success() {
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("tenant@ams.lk")
                .accountStatus(AccountStatus.ACTIVE)
                .userRoles(new HashSet<>())
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        lk.ac.kelaniya.ams.identity_access_service.dto.request.UpdateAccountStatusRequest request =
                lk.ac.kelaniya.ams.identity_access_service.dto.request.UpdateAccountStatusRequest.builder()
                        .status(AccountStatus.DEACTIVATED)
                        .reason("Tenancy agreement ended")
                        .build();

        AdminUserDetailResponse result = adminUserService.updateAccountStatus(userId, request, adminId);

        assertThat(result.getAccountStatus()).isEqualTo(AccountStatus.DEACTIVATED);
    }

    @Test
    @DisplayName("updateAccountStatus: SUSPENDED -> DEACTIVATED succeeds with reason")
    void testUpdateStatus_suspendedToDeactivated_withReason_success() {
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("tenant@ams.lk")
                .accountStatus(AccountStatus.SUSPENDED)
                .userRoles(new HashSet<>())
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        lk.ac.kelaniya.ams.identity_access_service.dto.request.UpdateAccountStatusRequest request =
                lk.ac.kelaniya.ams.identity_access_service.dto.request.UpdateAccountStatusRequest.builder()
                        .status(AccountStatus.DEACTIVATED)
                        .reason("Investigation concluded: account permanently closed")
                        .build();

        AdminUserDetailResponse result = adminUserService.updateAccountStatus(userId, request, adminId);

        assertThat(result.getAccountStatus()).isEqualTo(AccountStatus.DEACTIVATED);
    }

    @Test
    @DisplayName("updateAccountStatus: missing reason on SUSPENDED throws InvalidStatusTransitionException")
    void testUpdateStatus_suspended_missingReason_throwsException() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).accountStatus(AccountStatus.ACTIVE).userRoles(new HashSet<>()).build();
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        lk.ac.kelaniya.ams.identity_access_service.dto.request.UpdateAccountStatusRequest request =
                lk.ac.kelaniya.ams.identity_access_service.dto.request.UpdateAccountStatusRequest.builder()
                        .status(AccountStatus.SUSPENDED)
                        .reason("   ")
                        .build();

        assertThatThrownBy(() -> adminUserService.updateAccountStatus(userId, request, UUID.randomUUID()))
                .isInstanceOf(lk.ac.kelaniya.ams.identity_access_service.exception.InvalidStatusTransitionException.class)
                .hasMessageContaining("Reason is required");
    }

    @Test
    @DisplayName("updateAccountStatus: missing reason on REJECTED throws InvalidStatusTransitionException")
    void testUpdateStatus_rejected_missingReason_throwsException() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).accountStatus(AccountStatus.PENDING_VERIFICATION).userRoles(new HashSet<>()).build();
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        lk.ac.kelaniya.ams.identity_access_service.dto.request.UpdateAccountStatusRequest request =
                lk.ac.kelaniya.ams.identity_access_service.dto.request.UpdateAccountStatusRequest.builder()
                        .status(AccountStatus.REJECTED)
                        .reason(null)
                        .build();

        assertThatThrownBy(() -> adminUserService.updateAccountStatus(userId, request, UUID.randomUUID()))
                .isInstanceOf(lk.ac.kelaniya.ams.identity_access_service.exception.InvalidStatusTransitionException.class)
                .hasMessageContaining("Reason is required");
    }

    @Test
    @DisplayName("updateAccountStatus: missing reason on DEACTIVATED throws InvalidStatusTransitionException")
    void testUpdateStatus_deactivated_missingReason_throwsException() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).accountStatus(AccountStatus.ACTIVE).userRoles(new HashSet<>()).build();
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        lk.ac.kelaniya.ams.identity_access_service.dto.request.UpdateAccountStatusRequest request =
                lk.ac.kelaniya.ams.identity_access_service.dto.request.UpdateAccountStatusRequest.builder()
                        .status(AccountStatus.DEACTIVATED)
                        .build();

        assertThatThrownBy(() -> adminUserService.updateAccountStatus(userId, request, UUID.randomUUID()))
                .isInstanceOf(lk.ac.kelaniya.ams.identity_access_service.exception.InvalidStatusTransitionException.class)
                .hasMessageContaining("Reason is required");
    }

    @Test
    @DisplayName("updateAccountStatus: invalid transition REJECTED -> ACTIVE throws InvalidStatusTransitionException")
    void testUpdateStatus_rejectedToActive_throwsException() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).accountStatus(AccountStatus.REJECTED).userRoles(new HashSet<>()).build();
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        lk.ac.kelaniya.ams.identity_access_service.dto.request.UpdateAccountStatusRequest request =
                lk.ac.kelaniya.ams.identity_access_service.dto.request.UpdateAccountStatusRequest.builder()
                        .status(AccountStatus.ACTIVE)
                        .build();

        assertThatThrownBy(() -> adminUserService.updateAccountStatus(userId, request, UUID.randomUUID()))
                .isInstanceOf(lk.ac.kelaniya.ams.identity_access_service.exception.InvalidStatusTransitionException.class)
                .hasMessageContaining("Invalid account status transition");
    }

    @Test
    @DisplayName("updateAccountStatus: invalid transition DEACTIVATED -> PENDING_VERIFICATION throws InvalidStatusTransitionException")
    void testUpdateStatus_deactivatedToPending_throwsException() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).accountStatus(AccountStatus.DEACTIVATED).userRoles(new HashSet<>()).build();
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        lk.ac.kelaniya.ams.identity_access_service.dto.request.UpdateAccountStatusRequest request =
                lk.ac.kelaniya.ams.identity_access_service.dto.request.UpdateAccountStatusRequest.builder()
                        .status(AccountStatus.PENDING_VERIFICATION)
                        .build();

        assertThatThrownBy(() -> adminUserService.updateAccountStatus(userId, request, UUID.randomUUID()))
                .isInstanceOf(lk.ac.kelaniya.ams.identity_access_service.exception.InvalidStatusTransitionException.class)
                .hasMessageContaining("Invalid account status transition");
    }

    @Test
    @DisplayName("updateAccountStatus: transition to same status ACTIVE -> ACTIVE throws InvalidStatusTransitionException")
    void testUpdateStatus_sameStatus_throwsException() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).accountStatus(AccountStatus.ACTIVE).userRoles(new HashSet<>()).build();
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        lk.ac.kelaniya.ams.identity_access_service.dto.request.UpdateAccountStatusRequest request =
                lk.ac.kelaniya.ams.identity_access_service.dto.request.UpdateAccountStatusRequest.builder()
                        .status(AccountStatus.ACTIVE)
                        .build();

        assertThatThrownBy(() -> adminUserService.updateAccountStatus(userId, request, UUID.randomUUID()))
                .isInstanceOf(lk.ac.kelaniya.ams.identity_access_service.exception.InvalidStatusTransitionException.class)
                .hasMessageContaining("Invalid account status transition");
    }

    @Test
    @DisplayName("updateAccountStatus: nonexistent user throws UserNotFoundException")
    void testUpdateStatus_userNotFound_throwsException() {
        UUID missingId = UUID.randomUUID();
        given(userRepository.findById(missingId)).willReturn(Optional.empty());

        lk.ac.kelaniya.ams.identity_access_service.dto.request.UpdateAccountStatusRequest request =
                lk.ac.kelaniya.ams.identity_access_service.dto.request.UpdateAccountStatusRequest.builder()
                        .status(AccountStatus.ACTIVE)
                        .build();

        assertThatThrownBy(() -> adminUserService.updateAccountStatus(missingId, request, UUID.randomUUID()))
                .isInstanceOf(UserNotFoundException.class);
    }

    // =========================================================================
    // Role Assignment & Removal Tests (AMS1-S2-IAM-07)
    // =========================================================================

    @Test
    @DisplayName("assignRole: grants valid role to user by creating UserRole and returns updated details")
    void testAssignRole_success() {
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("staff@ams.lk")
                .firstName("Staff")
                .lastName("Member")
                .accountStatus(AccountStatus.ACTIVE)
                .requestedRole("FINANCE_OFFICER")
                .userRoles(new HashSet<>())
                .build();

        Role role = Role.builder().id(UUID.randomUUID()).name("FINANCE_OFFICER").build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(roleRepository.findByName("FINANCE_OFFICER")).willReturn(Optional.of(role));
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        AdminUserDetailResponse response = adminUserService.assignRole(userId, "FINANCE_OFFICER", adminId);

        assertThat(response).isNotNull();
        assertThat(response.getRoles()).containsExactly("FINANCE_OFFICER");
        assertThat(user.getUserRoles()).hasSize(1);
        verify(userRepository).save(user);
        verify(auditService).record(
                AuditEventType.ROLE_ASSIGNED,
                userId,
                adminId,
                null,
                "FINANCE_OFFICER",
                null
        );
    }

    @Test
    @DisplayName("assignRole: allows a user to hold multiple roles simultaneously without removing existing roles")
    void testAssignRole_multipleRolesSimultaneously() {
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Role existingRole = Role.builder().id(UUID.randomUUID()).name("OWNER").build();
        User user = User.builder()
                .id(userId)
                .email("multi@ams.lk")
                .accountStatus(AccountStatus.ACTIVE)
                .requestedRole("OWNER")
                .userRoles(new HashSet<>())
                .build();
        user.addRole(existingRole);

        Role newRole = Role.builder().id(UUID.randomUUID()).name("FINANCE_OFFICER").build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(roleRepository.findByName("FINANCE_OFFICER")).willReturn(Optional.of(newRole));
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        AdminUserDetailResponse response = adminUserService.assignRole(userId, "FINANCE_OFFICER", adminId);

        assertThat(response.getRoles()).containsExactlyInAnyOrder("OWNER", "FINANCE_OFFICER");
        assertThat(user.getUserRoles()).hasSize(2);
    }

    @Test
    @DisplayName("assignRole: successfully assigns a role DIFFERENT from requestedRole (point 3 requirement)")
    void testAssignRole_differentFromRequestedRole_succeeds() {
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("mismatch@ams.lk")
                .accountStatus(AccountStatus.PENDING_VERIFICATION)
                .requestedRole("OWNER") // User requested OWNER at registration
                .userRoles(new HashSet<>())
                .build();

        // Admin assigns a completely different role: TECHNICIAN
        Role technicianRole = Role.builder().id(UUID.randomUUID()).name("TECHNICIAN").build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(roleRepository.findByName("TECHNICIAN")).willReturn(Optional.of(technicianRole));
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        AdminUserDetailResponse response = adminUserService.assignRole(userId, "TECHNICIAN", adminId);

        assertThat(response).isNotNull();
        assertThat(response.getRequestedRole()).isEqualTo("OWNER");
        assertThat(response.getRoles()).containsExactly("TECHNICIAN");
    }

    @Test
    @DisplayName("assignRole: self-assignment guard blocks admin from assigning role to their own account")
    void testAssignRole_selfAssignment_blocked() {
        UUID adminId = UUID.randomUUID();

        assertThatThrownBy(() -> adminUserService.assignRole(adminId, "FINANCE_OFFICER", adminId))
                .isInstanceOf(SelfRoleAssignmentException.class)
                .hasMessageContaining("Administrators cannot assign roles to their own account.");
    }

    @Test
    @DisplayName("assignRole: invalid role name throws InvalidRoleException (400)")
    void testAssignRole_invalidRoleName_throwsException() {
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        assertThatThrownBy(() -> adminUserService.assignRole(userId, "SUPERUSER", adminId))
                .isInstanceOf(InvalidRoleException.class)
                .hasMessageContaining("Invalid role: 'SUPERUSER'");

        assertThatThrownBy(() -> adminUserService.assignRole(userId, null, adminId))
                .isInstanceOf(InvalidRoleException.class);

        assertThatThrownBy(() -> adminUserService.assignRole(userId, "   ", adminId))
                .isInstanceOf(InvalidRoleException.class);
    }

    @Test
    @DisplayName("assignRole: assigning already-held role throws RoleAlreadyAssignedException (409)")
    void testAssignRole_alreadyHeld_throwsException() {
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Role existingRole = Role.builder().id(UUID.randomUUID()).name("FINANCE_OFFICER").build();
        User user = User.builder()
                .id(userId)
                .accountStatus(AccountStatus.ACTIVE)
                .userRoles(new HashSet<>())
                .build();
        user.addRole(existingRole);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> adminUserService.assignRole(userId, "FINANCE_OFFICER", adminId))
                .isInstanceOf(RoleAlreadyAssignedException.class)
                .hasMessageContaining("User already holds the role: FINANCE_OFFICER");
    }

    @Test
    @DisplayName("assignRole: target user not found throws UserNotFoundException (404)")
    void testAssignRole_userNotFound_throwsException() {
        UUID missingId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        given(userRepository.findById(missingId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.assignRole(missingId, "FINANCE_OFFICER", adminId))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("assignRole: does NOT alter account status for PENDING_VERIFICATION account")
    void testAssignRole_preservesPendingVerificationStatus() {
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .accountStatus(AccountStatus.PENDING_VERIFICATION)
                .requestedRole("SECURITY_OFFICER")
                .userRoles(new HashSet<>())
                .build();

        Role role = Role.builder().id(UUID.randomUUID()).name("SECURITY_OFFICER").build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(roleRepository.findByName("SECURITY_OFFICER")).willReturn(Optional.of(role));
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        AdminUserDetailResponse response = adminUserService.assignRole(userId, "SECURITY_OFFICER", adminId);

        assertThat(response.getAccountStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
    }

    @Test
    @DisplayName("assignRole: does NOT alter account status for ACTIVE account")
    void testAssignRole_preservesActiveStatus() {
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .accountStatus(AccountStatus.ACTIVE)
                .requestedRole("APARTMENT_MANAGER")
                .userRoles(new HashSet<>())
                .build();

        Role role = Role.builder().id(UUID.randomUUID()).name("APARTMENT_MANAGER").build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(roleRepository.findByName("APARTMENT_MANAGER")).willReturn(Optional.of(role));
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        AdminUserDetailResponse response = adminUserService.assignRole(userId, "APARTMENT_MANAGER", adminId);

        assertThat(response.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("removeRole: removes specific granted role and leaves other roles intact")
    void testRemoveRole_success() {
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Role role1 = Role.builder().id(UUID.randomUUID()).name("FINANCE_OFFICER").build();
        Role role2 = Role.builder().id(UUID.randomUUID()).name("TENANT_RESIDENT").build();

        User user = User.builder()
                .id(userId)
                .accountStatus(AccountStatus.ACTIVE)
                .userRoles(new HashSet<>())
                .build();
        user.addRole(role1);
        user.addRole(role2);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(roleRepository.findByName("FINANCE_OFFICER")).willReturn(Optional.of(role1));
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        AdminUserDetailResponse response = adminUserService.removeRole(userId, "FINANCE_OFFICER", adminId);

        assertThat(response.getRoles()).containsExactly("TENANT_RESIDENT");
        assertThat(user.getUserRoles()).hasSize(1);
        verify(userRepository).save(user);
        verify(auditService).record(
                AuditEventType.ROLE_REMOVED,
                userId,
                adminId,
                "FINANCE_OFFICER",
                null,
                null
        );
    }

    @Test
    @DisplayName("removeRole: self-removal guard blocks admin from removing role from their own account")
    void testRemoveRole_selfRemoval_blocked() {
        UUID adminId = UUID.randomUUID();

        assertThatThrownBy(() -> adminUserService.removeRole(adminId, "SYSTEM_ADMINISTRATOR", adminId))
                .isInstanceOf(SelfRoleAssignmentException.class)
                .hasMessageContaining("Administrators cannot remove roles from their own account.");
    }

    @Test
    @DisplayName("removeRole: invalid role name throws InvalidRoleException (400)")
    void testRemoveRole_invalidRoleName_throwsException() {
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        assertThatThrownBy(() -> adminUserService.removeRole(userId, "NOT_A_ROLE", adminId))
                .isInstanceOf(InvalidRoleException.class)
                .hasMessageContaining("Invalid role: 'NOT_A_ROLE'");
    }

    @Test
    @DisplayName("removeRole: removing a role the user does not have throws RoleNotAssignedException (409)")
    void testRemoveRole_notHeld_throwsException() {
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .accountStatus(AccountStatus.ACTIVE)
                .userRoles(new HashSet<>())
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> adminUserService.removeRole(userId, "SECURITY_OFFICER", adminId))
                .isInstanceOf(RoleNotAssignedException.class)
                .hasMessageContaining("User does not hold the role: SECURITY_OFFICER");
    }

    @Test
    @DisplayName("removeRole: target user not found throws UserNotFoundException (404)")
    void testRemoveRole_userNotFound_throwsException() {
        UUID missingId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        given(userRepository.findById(missingId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.removeRole(missingId, "SECURITY_OFFICER", adminId))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("removeRole: does NOT alter account status")
    void testRemoveRole_preservesAccountStatus() {
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Role role = Role.builder().id(UUID.randomUUID()).name("MAINTENANCE_COORDINATOR").build();

        User user = User.builder()
                .id(userId)
                .accountStatus(AccountStatus.ACTIVE)
                .userRoles(new HashSet<>())
                .build();
        user.addRole(role);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(roleRepository.findByName("MAINTENANCE_COORDINATOR")).willReturn(Optional.of(role));
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        AdminUserDetailResponse response = adminUserService.removeRole(userId, "MAINTENANCE_COORDINATOR", adminId);

        assertThat(response.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("createUser: hashes temporary password, sets ACTIVE status and mustChangePassword=true, requestedRole=null, zero roles")
    void testCreateUser_success() {
        UUID expectedId = UUID.randomUUID();
        AdminCreateUserRequest request = AdminCreateUserRequest.builder()
                .firstName("  Jane  ")
                .lastName("  Doe  ")
                .email("  Jane.Doe@AMS.lk  ")
                .phone("  +94771234567  ")
                .temporaryPassword("TempSecret123")
                .build();

        given(userRepository.existsByEmail("jane.doe@ams.lk")).willReturn(false);
        given(passwordEncoder.encode("TempSecret123")).willReturn("$2a$10$hashedBCryptPassword");
        given(userRepository.save(any(User.class))).willAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(expectedId);
            return u;
        });

        AdminCreateUserResponse response = adminUserService.createUser(request);

        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(expectedId);
        assertThat(response.getEmail()).isEqualTo("jane.doe@ams.lk");
        assertThat(response.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(response.isMustChangePassword()).isTrue();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User savedUser = captor.getValue();

        assertThat(savedUser.getId()).isEqualTo(expectedId);
        assertThat(savedUser.getEmail()).isEqualTo("jane.doe@ams.lk");
        assertThat(savedUser.getUsername()).isEqualTo("jane.doe@ams.lk");
        assertThat(savedUser.getFirstName()).isEqualTo("Jane");
        assertThat(savedUser.getLastName()).isEqualTo("Doe");
        assertThat(savedUser.getPhone()).isEqualTo("+94771234567");
        assertThat(savedUser.getPasswordHash()).isEqualTo("$2a$10$hashedBCryptPassword");
        assertThat(savedUser.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(savedUser.isMustChangePassword()).isTrue();
        assertThat(savedUser.getRequestedRole()).isNull();
        assertThat(savedUser.getFailedAttemptCount()).isZero();
        assertThat(savedUser.getUserRoles()).isEmpty();
        verify(auditService).record(
                AuditEventType.USER_CREATED_BY_ADMIN,
                expectedId,
                null,
                null,
                "jane.doe@ams.lk",
                null
        );
    }

    @Test
    @DisplayName("createUser: throws DuplicateEmailException when email is already in use")
    void testCreateUser_duplicateEmail_throwsDuplicateEmailException() {
        AdminCreateUserRequest request = AdminCreateUserRequest.builder()
                .firstName("Jane")
                .lastName("Doe")
                .email("duplicate@ams.lk")
                .phone("+94771234567")
                .temporaryPassword("TempSecret123")
                .build();

        given(userRepository.existsByEmail("duplicate@ams.lk")).willReturn(true);

        assertThatThrownBy(() -> adminUserService.createUser(request))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessage("Email already in use");

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }
}

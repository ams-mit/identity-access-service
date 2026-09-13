package lk.ac.kelaniya.ams.identity_access_service.service;

import lk.ac.kelaniya.ams.identity_access_service.dto.response.AdminUserDetailResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.AdminUserSummaryResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lk.ac.kelaniya.ams.identity_access_service.entity.Role;
import lk.ac.kelaniya.ams.identity_access_service.entity.User;
import lk.ac.kelaniya.ams.identity_access_service.entity.UserRole;
import lk.ac.kelaniya.ams.identity_access_service.exception.UserNotFoundException;
import lk.ac.kelaniya.ams.identity_access_service.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

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
}

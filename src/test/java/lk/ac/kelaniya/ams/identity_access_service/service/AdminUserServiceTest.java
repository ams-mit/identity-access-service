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
}

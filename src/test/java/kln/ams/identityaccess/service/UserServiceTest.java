package kln.ams.identityaccess.service;

import kln.ams.identityaccess.dto.response.PagedData;
import kln.ams.identityaccess.dto.user.CreateUserRequest;
import kln.ams.identityaccess.dto.user.ReplaceUserRolesRequest;
import kln.ams.identityaccess.dto.user.UpdateStatusRequest;
import kln.ams.identityaccess.dto.user.UpdateUserRequest;
import kln.ams.identityaccess.dto.user.UserResponse;
import kln.ams.identityaccess.dto.user.UserRolesResponse;
import kln.ams.identityaccess.entity.AccountStatus;
import kln.ams.identityaccess.entity.Role;
import kln.ams.identityaccess.entity.User;
import kln.ams.identityaccess.exception.DuplicateResourceException;
import kln.ams.identityaccess.exception.InvalidRoleException;
import kln.ams.identityaccess.exception.UserNotFoundException;
import kln.ams.identityaccess.repository.RoleRepository;
import kln.ams.identityaccess.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private Role ownerRole;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        ownerRole = Role.builder()
                .id(UUID.randomUUID())
                .name("OWNER")
                .description("Owner")
                .active(true)
                .build();

        testUser = User.builder()
                .id(userId)
                .username("owner@example.com")
                .email("owner@example.com")
                .passwordHash("hashed")
                .firstName("Alice")
                .lastName("Silva")
                .phone("0779998888")
                .accountStatus(AccountStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .userRoles(new HashSet<>())
                .build();

        testUser.addRole(ownerRole);
    }

    @Test
    void getUsers_ReturnsPagedData() {
        Page<User> page = new PageImpl<>(List.of(testUser));
        when(userRepository.findUsersFiltered(any(), any(), any(), any(Pageable.class))).thenReturn(page);

        PagedData<UserResponse> result = userService.getUsers(0, 20, null, null, null);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getUsername()).isEqualTo("owner@example.com");
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void createUser_Success() {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("newuser@example.com")
                .email("newuser@example.com")
                .password("Password123!")
                .firstName("Bob")
                .lastName("Smith")
                .phone("0771112222")
                .roles(List.of("OWNER"))
                .build();

        when(userRepository.existsByUsernameIgnoreCase("newuser@example.com")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("newuser@example.com")).thenReturn(false);
        when(roleRepository.findByNameIgnoreCase("OWNER")).thenReturn(Optional.of(ownerRole));
        when(passwordEncoder.encode("Password123!")).thenReturn("hashed_new");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        UserResponse response = userService.createUser(request);

        assertThat(response.getUsername()).isEqualTo("newuser@example.com");
        assertThat(response.getEmail()).isEqualTo("newuser@example.com");
        assertThat(response.getRoles()).contains("OWNER");
    }

    @Test
    void createUser_DuplicateUsername_ThrowsException() {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("owner@example.com")
                .email("new@example.com")
                .password("Password123!")
                .firstName("Bob")
                .lastName("Smith")
                .roles(List.of("OWNER"))
                .build();

        when(userRepository.existsByUsernameIgnoreCase("owner@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void createUser_NonCanonicalRole_ThrowsException() {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("new@example.com")
                .email("new@example.com")
                .password("Password123!")
                .firstName("Bob")
                .lastName("Smith")
                .roles(List.of("LEGACY_ADMIN"))
                .build();

        when(userRepository.existsByUsernameIgnoreCase("new@example.com")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(InvalidRoleException.class);
    }

    @Test
    void updateUser_Success() {
        UpdateUserRequest request = UpdateUserRequest.builder()
                .firstName("Alicia")
                .lastName("Silva-Updated")
                .phone("0771234567")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        UserResponse response = userService.updateUser(userId, request);

        assertThat(response.getFirstName()).isEqualTo("Alicia");
        assertThat(response.getLastName()).isEqualTo("Silva-Updated");
        assertThat(response.getPhone()).isEqualTo("0771234567");
    }

    @Test
    void updateStatus_Success() {
        UpdateStatusRequest request = new UpdateStatusRequest(AccountStatus.SUSPENDED);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        UserResponse response = userService.updateStatus(userId, request);

        assertThat(response.getStatus()).isEqualTo("SUSPENDED");
    }

    @Test
    void getUserRoles_Success() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        UserRolesResponse response = userService.getUserRoles(userId);

        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getRoles()).contains("OWNER");
    }

    @Test
    void replaceUserRoles_Success() {
        Role adminRole = Role.builder()
                .id(UUID.randomUUID())
                .name("SYSTEM_ADMINISTRATOR")
                .build();

        ReplaceUserRolesRequest request = new ReplaceUserRolesRequest(List.of("SYSTEM_ADMINISTRATOR"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(roleRepository.findByNameIgnoreCase("SYSTEM_ADMINISTRATOR")).thenReturn(Optional.of(adminRole));

        UserRolesResponse response = userService.replaceUserRoles(userId, request);

        assertThat(response.getRoles()).containsExactly("SYSTEM_ADMINISTRATOR");
    }
}

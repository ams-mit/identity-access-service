package kln.ams.identityaccess.service;

import kln.ams.identityaccess.dto.auth.AuthenticatedUserResponse;
import kln.ams.identityaccess.dto.auth.LoginRequest;
import kln.ams.identityaccess.dto.auth.LoginResponse;
import kln.ams.identityaccess.entity.AccountStatus;
import kln.ams.identityaccess.entity.Role;
import kln.ams.identityaccess.entity.User;
import kln.ams.identityaccess.entity.UserRole;
import kln.ams.identityaccess.exception.AccountInactiveException;
import kln.ams.identityaccess.exception.InvalidCredentialsException;
import kln.ams.identityaccess.exception.UserNotFoundException;
import kln.ams.identityaccess.repository.UserRepository;
import kln.ams.identityaccess.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private Role tenantRole;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        tenantRole = Role.builder()
                .id(UUID.randomUUID())
                .name("TENANT_RESIDENT")
                .description("Tenant")
                .active(true)
                .build();

        testUser = User.builder()
                .id(userId)
                .username("john@example.com")
                .email("john@example.com")
                .passwordHash("hashed_pw")
                .firstName("John")
                .lastName("Perera")
                .accountStatus(AccountStatus.ACTIVE)
                .userRoles(new HashSet<>())
                .build();

        testUser.addRole(tenantRole);
    }

    @Test
    void login_Success() {
        LoginRequest request = new LoginRequest("john@example.com", "Password123!");

        when(userRepository.findByUsernameIgnoreCase("john@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Password123!", "hashed_pw")).thenReturn(true);
        when(jwtService.generateToken(eq(userId), any())).thenReturn("mock_token_jwt");

        LoginResponse response = authService.login(request);

        assertThat(response.getAccessToken()).isEqualTo("mock_token_jwt");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(1800);
        assertThat(response.getUser().getUsername()).isEqualTo("john@example.com");
        assertThat(response.getUser().getRoles()).contains("TENANT_RESIDENT");
    }

    @Test
    void login_InvalidPassword_ThrowsException() {
        LoginRequest request = new LoginRequest("john@example.com", "WrongPassword");

        when(userRepository.findByUsernameIgnoreCase("john@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("WrongPassword", "hashed_pw")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_UserNotFound_ThrowsException() {
        LoginRequest request = new LoginRequest("unknown@example.com", "Password123!");

        when(userRepository.findByUsernameIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_InactiveAccount_ThrowsException() {
        testUser.setAccountStatus(AccountStatus.INACTIVE);
        LoginRequest request = new LoginRequest("john@example.com", "Password123!");

        when(userRepository.findByUsernameIgnoreCase("john@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Password123!", "hashed_pw")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AccountInactiveException.class);
    }

    @Test
    void login_LockedAccount_ThrowsException() {
        testUser.setLockedUntil(Instant.now().plusSeconds(600));
        LoginRequest request = new LoginRequest("john@example.com", "Password123!");

        when(userRepository.findByUsernameIgnoreCase("john@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Password123!", "hashed_pw")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AccountInactiveException.class);
    }

    @Test
    void getCurrentUser_Success() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        AuthenticatedUserResponse response = authService.getCurrentUser(userId);

        assertThat(response.getId()).isEqualTo(userId);
        assertThat(response.getUsername()).isEqualTo("john@example.com");
        assertThat(response.getEmail()).isEqualTo("john@example.com");
        assertThat(response.getRoles()).contains("TENANT_RESIDENT");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void getCurrentUser_NotFound_ThrowsException() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getCurrentUser(userId))
                .isInstanceOf(UserNotFoundException.class);
    }
}

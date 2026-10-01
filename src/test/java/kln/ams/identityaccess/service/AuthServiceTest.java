package kln.ams.identityaccess.service;

import kln.ams.identityaccess.dto.auth.AuthenticatedUserResponse;
import kln.ams.identityaccess.dto.auth.ChangePasswordRequest;
import kln.ams.identityaccess.dto.auth.ForgotPasswordRequest;
import kln.ams.identityaccess.dto.auth.LoginRequest;
import kln.ams.identityaccess.dto.auth.LoginResponse;
import kln.ams.identityaccess.dto.auth.MessageResponse;
import kln.ams.identityaccess.dto.auth.RegisterRequest;
import kln.ams.identityaccess.dto.auth.RegisterResponse;
import kln.ams.identityaccess.dto.auth.ResetPasswordRequest;
import kln.ams.identityaccess.entity.AccountStatus;
import kln.ams.identityaccess.entity.PasswordResetToken;
import kln.ams.identityaccess.entity.Role;
import kln.ams.identityaccess.entity.User;
import kln.ams.identityaccess.exception.AccountInactiveException;
import kln.ams.identityaccess.exception.InvalidCredentialsException;
import kln.ams.identityaccess.exception.InvalidResetTokenException;
import kln.ams.identityaccess.exception.PasswordValidationException;
import kln.ams.identityaccess.exception.UserAlreadyExistsException;
import kln.ams.identityaccess.exception.UserNotFoundException;
import kln.ams.identityaccess.repository.PasswordResetTokenRepository;
import kln.ams.identityaccess.repository.RoleRepository;
import kln.ams.identityaccess.repository.UserRepository;
import kln.ams.identityaccess.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private Environment environment;

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

    // ==========================================
    // AUTH-001: Login
    // ==========================================

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
                .isInstanceOf(AccountInactiveException.class)
                .hasMessage("Account is inactive or suspended");
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

    // ==========================================
    // AUTH-002: Current User
    // ==========================================

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

    // ==========================================
    // AUTH-003: Logout
    // ==========================================

    @Test
    void logout_Success() {
        authService.logout(userId);
        // Stateless logout succeeds without exceptions
    }

    // ==========================================
    // AUTH-004: Register
    // ==========================================

    @Test
    void register_Success_CreatesInactiveUserWithTenantResidentRole() {
        RegisterRequest request = RegisterRequest.builder()
                .firstName("Alice")
                .lastName("Walker")
                .email("alice.walker@ams.lk")
                .phone("+94779876543")
                .password("Password123")
                .confirmPassword("Password123")
                .build();

        when(userRepository.existsByEmailIgnoreCase("alice.walker@ams.lk")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("alice.walker@ams.lk")).thenReturn(false);
        when(roleRepository.findByNameIgnoreCase("TENANT_RESIDENT")).thenReturn(Optional.of(tenantRole));
        when(passwordEncoder.encode("Password123")).thenReturn("encoded_pass");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(userCaptor.capture())).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(userId);
            return u;
        });

        RegisterResponse response = authService.register(request);

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getUsername()).isEqualTo("alice.walker@ams.lk");
        assertThat(savedUser.getEmail()).isEqualTo("alice.walker@ams.lk");
        assertThat(savedUser.getAccountStatus()).isEqualTo(AccountStatus.INACTIVE);
        assertThat(savedUser.getPasswordHash()).isEqualTo("encoded_pass");
        assertThat(savedUser.getUserRoles()).anyMatch(ur -> ur.getRole().getName().equals("TENANT_RESIDENT"));

        assertThat(response.getStatus()).isEqualTo("INACTIVE");
        assertThat(response.getRoles()).contains("TENANT_RESIDENT");
        assertThat(response.getUsername()).isEqualTo("alice.walker@ams.lk");
    }

    @Test
    void register_PasswordMismatch_ThrowsException() {
        RegisterRequest request = RegisterRequest.builder()
                .firstName("Alice")
                .lastName("Walker")
                .email("alice@ams.lk")
                .password("Password123")
                .confirmPassword("MismatchedPass")
                .build();

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(PasswordValidationException.class)
                .hasMessage("Passwords do not match");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_DuplicateEmail_ThrowsException() {
        RegisterRequest request = RegisterRequest.builder()
                .firstName("Alice")
                .lastName("Walker")
                .email("alice@ams.lk")
                .password("Password123")
                .confirmPassword("Password123")
                .build();

        when(userRepository.existsByEmailIgnoreCase("alice@ams.lk")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessage("User already exists with this email or username");

        verify(userRepository, never()).save(any());
    }

    // ==========================================
    // AUTH-005: Forgot Password
    // ==========================================

    @Test
    void forgotPassword_ActiveUser_GeneratesTokenAndInvalidatesPrevious() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("john@example.com");

        when(userRepository.findByEmailIgnoreCase("john@example.com")).thenReturn(Optional.of(testUser));

        MessageResponse response = authService.forgotPassword(request);

        assertThat(response.getMessage()).isEqualTo(AuthService.FORGOT_PASSWORD_GENERIC_MESSAGE);
        verify(passwordResetTokenRepository).invalidateAllActiveTokensForUser(eq(testUser), any(Instant.class));
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
    }

    @Test
    void forgotPassword_NonExistentEmail_ReturnsSameGenericMessage() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("unknown@example.com");

        when(userRepository.findByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());

        MessageResponse response = authService.forgotPassword(request);

        assertThat(response.getMessage()).isEqualTo(AuthService.FORGOT_PASSWORD_GENERIC_MESSAGE);
        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    void forgotPassword_InactiveUser_ReturnsSameGenericMessageWithoutTokenGeneration() {
        testUser.setAccountStatus(AccountStatus.INACTIVE);
        ForgotPasswordRequest request = new ForgotPasswordRequest("john@example.com");

        when(userRepository.findByEmailIgnoreCase("john@example.com")).thenReturn(Optional.of(testUser));

        MessageResponse response = authService.forgotPassword(request);

        assertThat(response.getMessage()).isEqualTo(AuthService.FORGOT_PASSWORD_GENERIC_MESSAGE);
        verify(passwordResetTokenRepository, never()).save(any());
    }

    // ==========================================
    // AUTH-006: Reset Password
    // ==========================================

    @Test
    void resetPassword_Success_UpdatesPasswordAndInvalidatesTokens() throws Exception {
        String rawToken = "valid-token-123";
        String tokenHash = sha256Hex(rawToken);

        ResetPasswordRequest request = new ResetPasswordRequest(rawToken, "NewPassword123", "NewPassword123");

        PasswordResetToken token = PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                .usedAt(null)
                .build();

        when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NewPassword123")).thenReturn("new_hash_456");

        MessageResponse response = authService.resetPassword(request);

        assertThat(response.getMessage()).isEqualTo(AuthService.RESET_PASSWORD_SUCCESS_MESSAGE);
        assertThat(token.getUsedAt()).isNotNull();
        assertThat(testUser.getPasswordHash()).isEqualTo("new_hash_456");

        verify(userRepository).save(testUser);
        verify(passwordResetTokenRepository).save(token);
        verify(passwordResetTokenRepository).invalidateAllActiveTokensForUser(eq(testUser), any(Instant.class));
    }

    @Test
    void resetPassword_PasswordMismatch_ThrowsException() {
        ResetPasswordRequest request = new ResetPasswordRequest("token-123", "NewPassword123", "Different456");

        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(PasswordValidationException.class)
                .hasMessage("New password and confirm password do not match");

        verify(passwordResetTokenRepository, never()).findByTokenHash(any());
    }

    @Test
    void resetPassword_TokenNotFound_ThrowsInvalidResetTokenException() throws Exception {
        String rawToken = "unknown-token";
        String tokenHash = sha256Hex(rawToken);

        ResetPasswordRequest request = new ResetPasswordRequest(rawToken, "NewPassword123", "NewPassword123");

        when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(InvalidResetTokenException.class);
    }

    @Test
    void resetPassword_TokenExpired_ThrowsInvalidResetTokenException() throws Exception {
        String rawToken = "expired-token";
        String tokenHash = sha256Hex(rawToken);

        ResetPasswordRequest request = new ResetPasswordRequest(rawToken, "NewPassword123", "NewPassword123");

        PasswordResetToken token = PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                .usedAt(null)
                .build();

        when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(InvalidResetTokenException.class);
    }

    @Test
    void resetPassword_TokenAlreadyUsed_ThrowsInvalidResetTokenException() throws Exception {
        String rawToken = "used-token";
        String tokenHash = sha256Hex(rawToken);

        ResetPasswordRequest request = new ResetPasswordRequest(rawToken, "NewPassword123", "NewPassword123");

        PasswordResetToken token = PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                .usedAt(Instant.now().minus(1, ChronoUnit.MINUTES))
                .build();

        when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(InvalidResetTokenException.class);
    }

    @Test
    void resetPassword_UserInactive_ThrowsInvalidResetTokenException() throws Exception {
        String rawToken = "valid-token";
        String tokenHash = sha256Hex(rawToken);

        testUser.setAccountStatus(AccountStatus.INACTIVE);
        ResetPasswordRequest request = new ResetPasswordRequest(rawToken, "NewPassword123", "NewPassword123");

        PasswordResetToken token = PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                .usedAt(null)
                .build();

        when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(InvalidResetTokenException.class);
    }

    // ==========================================
    // AUTH-007: Change Password
    // ==========================================

    @Test
    void changePassword_Success() {
        ChangePasswordRequest request = new ChangePasswordRequest("OldPassword123", "NewPassword456", "NewPassword456");

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("OldPassword123", "hashed_pw")).thenReturn(true);
        when(passwordEncoder.matches("NewPassword456", "hashed_pw")).thenReturn(false);
        when(passwordEncoder.encode("NewPassword456")).thenReturn("new_hashed_pw");

        authService.changePassword(userId, request);

        assertThat(testUser.getPasswordHash()).isEqualTo("new_hashed_pw");
        verify(userRepository).save(testUser);
    }

    @Test
    void changePassword_WrongCurrentPassword_Throws400InvalidCredentials() {
        ChangePasswordRequest request = new ChangePasswordRequest("WrongPassword", "NewPassword456", "NewPassword456");

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("WrongPassword", "hashed_pw")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(userId, request))
                .isInstanceOf(InvalidCredentialsException.class)
                .satisfies(ex -> {
                    InvalidCredentialsException ice = (InvalidCredentialsException) ex;
                    assertThat(ice.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ice.getCode()).isEqualTo("INVALID_CREDENTIALS");
                });

        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_SamePassword_ThrowsValidationError() {
        ChangePasswordRequest request = new ChangePasswordRequest("Password123!", "Password123!", "Password123!");

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Password123!", "hashed_pw")).thenReturn(true);

        assertThatThrownBy(() -> authService.changePassword(userId, request))
                .isInstanceOf(PasswordValidationException.class)
                .hasMessage("New password must differ from current password");

        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_PasswordMismatch_ThrowsValidationError() {
        ChangePasswordRequest request = new ChangePasswordRequest("OldPassword123", "NewPassword456", "Mismatched789");

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("OldPassword123", "hashed_pw")).thenReturn(true);

        assertThatThrownBy(() -> authService.changePassword(userId, request))
                .isInstanceOf(PasswordValidationException.class)
                .hasMessage("New password and confirm password do not match");

        verify(userRepository, never()).save(any());
    }

    private String sha256Hex(String raw) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}

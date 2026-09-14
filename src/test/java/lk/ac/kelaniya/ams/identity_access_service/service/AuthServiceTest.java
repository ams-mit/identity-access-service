package lk.ac.kelaniya.ams.identity_access_service.service;

import lk.ac.kelaniya.ams.identity_access_service.dto.request.LoginRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.request.RegisterRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.LoginResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.RegisterResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lk.ac.kelaniya.ams.identity_access_service.entity.Role;
import lk.ac.kelaniya.ams.identity_access_service.entity.User;
import lk.ac.kelaniya.ams.identity_access_service.exception.AccountLockedException;
import lk.ac.kelaniya.ams.identity_access_service.exception.AccountStatusException;
import lk.ac.kelaniya.ams.identity_access_service.exception.DuplicateEmailException;
import lk.ac.kelaniya.ams.identity_access_service.exception.InvalidCredentialsException;
import lk.ac.kelaniya.ams.identity_access_service.exception.PasswordMismatchException;
import lk.ac.kelaniya.ams.identity_access_service.repository.UserRepository;
import lk.ac.kelaniya.ams.identity_access_service.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

    private RegisterRequest validRegisterRequest;
    private LoginRequest validLoginRequest;

    @BeforeEach
    void setUp() {
        validRegisterRequest = RegisterRequest.builder()
                .firstName("Alice")
                .lastName("Smith")
                .email("alice.smith@example.com")
                .phone("+94712345678")
                .requestedRole("OWNER")
                .password("StrongPassword1")
                .confirmPassword("StrongPassword1")
                .build();

        validLoginRequest = LoginRequest.builder()
                .email("alice.smith@example.com")
                .password("StrongPassword1")
                .build();
    }

    @Test
    @DisplayName("register creates user with PENDING_VERIFICATION and hashes password")
    void testRegister_success() {
        UUID expectedId = UUID.randomUUID();
        String hashedPassword = "$2a$10$hashedPasswordSample";

        given(userRepository.existsByEmail("alice.smith@example.com")).willReturn(false);
        given(passwordEncoder.encode("StrongPassword1")).willReturn(hashedPassword);
        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(expectedId);
            return user;
        });

        RegisterResponse response = authService.register(validRegisterRequest);

        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(expectedId);
        assertThat(response.getEmail()).isEqualTo("alice.smith@example.com");
        assertThat(response.getAccountStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(response.getRequestedRole()).isEqualTo("OWNER");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getPasswordHash()).isEqualTo(hashedPassword);
        assertThat(savedUser.getPasswordHash()).isNotEqualTo("StrongPassword1");
        assertThat(savedUser.getAccountStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(savedUser.getRequestedRole()).isEqualTo("OWNER");
        assertThat(savedUser.getFailedAttemptCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SYSTEM_ADMINISTRATOR",
            "APARTMENT_MANAGER",
            "OWNER",
            "TENANT_RESIDENT",
            "FINANCE_OFFICER",
            "MAINTENANCE_COORDINATOR",
            "TECHNICIAN",
            "SECURITY_OFFICER"
    })
    @DisplayName("register: each valid requested role persists correctly and grants ZERO roles (critical security regression test)")
    void testRegister_eachValidRole_persistsRequestedRole_andGrantsZeroRoles(String roleName) {
        RegisterRequest request = RegisterRequest.builder()
                .firstName("Bob")
                .lastName("Builder")
                .email("bob." + roleName.toLowerCase() + "@example.com")
                .phone("+94711112222")
                .requestedRole(roleName)
                .password("StrongPassword1")
                .confirmPassword("StrongPassword1")
                .build();

        UUID expectedId = UUID.randomUUID();
        given(userRepository.existsByEmail(request.getEmail())).willReturn(false);
        given(passwordEncoder.encode("StrongPassword1")).willReturn("hashed-password");
        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(expectedId);
            return user;
        });

        RegisterResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(expectedId);
        assertThat(response.getEmail()).isEqualTo(request.getEmail());
        assertThat(response.getAccountStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(response.getRequestedRole()).isEqualTo(roleName);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User savedUser = captor.getValue();

        // 1. Verify advisory requested role is persisted as plain data
        assertThat(savedUser.getRequestedRole()).isEqualTo(roleName);

        // 2. CRITICAL SECURITY REGRESSION TEST:
        // Confirm no UserRole/Role grant is created as a side effect of registration regardless of which role was requested.
        // Assert user has zero roles immediately after registration.
        assertThat(savedUser.getUserRoles()).isNotNull();
        assertThat(savedUser.getUserRoles()).isEmpty();
        assertThat(savedUser.getUserRoles()).hasSize(0);
    }

    @Test
    @DisplayName("register throws PasswordMismatchException when passwords differ")
    void testRegister_passwordMismatch() {
        validRegisterRequest.setConfirmPassword("MismatchPassword2");

        assertThatThrownBy(() -> authService.register(validRegisterRequest))
                .isInstanceOf(PasswordMismatchException.class)
                .hasMessage("Passwords do not match");

        verify(userRepository, never()).existsByEmail(any());
        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("register throws DuplicateEmailException when email exists")
    void testRegister_duplicateEmail() {
        given(userRepository.existsByEmail("alice.smith@example.com")).willReturn(true);

        assertThatThrownBy(() -> authService.register(validRegisterRequest))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessage("Email already in use");

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("login succeeds for ACTIVE user with matching password, issuing RS256 token")
    void testLogin_success() {
        UUID userId = UUID.randomUUID();
        User activeUser = User.builder()
                .id(userId)
                .email("alice.smith@example.com")
                .passwordHash("$2a$10$hashedPassword")
                .accountStatus(AccountStatus.ACTIVE)
                .build();
        Role role = Role.builder().name("RESIDENT").build();
        activeUser.addRole(role);

        given(userRepository.findByEmail("alice.smith@example.com")).willReturn(Optional.of(activeUser));
        given(passwordEncoder.matches("StrongPassword1", "$2a$10$hashedPassword")).willReturn(true);
        given(jwtService.generateToken(eq(userId), eq("alice.smith@example.com"), any())).willReturn("mock.jwt.token");
        given(jwtService.getExpirationSeconds()).willReturn(1800L);

        LoginResponse response = authService.login(validLoginRequest);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("mock.jwt.token");
        assertThat(response.getExpiresIn()).isEqualTo(1800L);
        assertThat(response.getUser()).isNotNull();
        assertThat(response.getUser().getUserId()).isEqualTo(userId);
        assertThat(response.getUser().getEmail()).isEqualTo("alice.smith@example.com");
        assertThat(response.getUser().getRoles()).containsExactly("RESIDENT");
    }

    @Test
    @DisplayName("login throws generic InvalidCredentialsException when email is not found")
    void testLogin_unknownEmail_throwsGeneric401() {
        given(userRepository.findByEmail("alice.smith@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(validLoginRequest))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password.");

        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtService, never()).generateToken(any(), any(), any());
    }

    @Test
    @DisplayName("login throws generic InvalidCredentialsException with identical message when password is wrong")
    void testLogin_wrongPassword_throwsGeneric401() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("alice.smith@example.com")
                .passwordHash("$2a$10$hashedPassword")
                .accountStatus(AccountStatus.ACTIVE)
                .build();

        given(userRepository.findByEmail("alice.smith@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("StrongPassword1", "$2a$10$hashedPassword")).willReturn(false);

        assertThatThrownBy(() -> authService.login(validLoginRequest))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password.");

        verify(jwtService, never()).generateToken(any(), any(), any());
    }

    @Test
    @DisplayName("login rejects PENDING_VERIFICATION account with 403 after password matches")
    void testLogin_pendingVerification_throws403() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("alice.smith@example.com")
                .passwordHash("$2a$10$hashedPassword")
                .accountStatus(AccountStatus.PENDING_VERIFICATION)
                .build();

        given(userRepository.findByEmail("alice.smith@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("StrongPassword1", "$2a$10$hashedPassword")).willReturn(true);

        assertThatThrownBy(() -> authService.login(validLoginRequest))
                .isInstanceOf(AccountStatusException.class)
                .hasMessageContaining("pending verification");

        verify(jwtService, never()).generateToken(any(), any(), any());
    }

    @Test
    @DisplayName("login rejects SUSPENDED account with 403 after password matches")
    void testLogin_suspended_throws403() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("alice.smith@example.com")
                .passwordHash("$2a$10$hashedPassword")
                .accountStatus(AccountStatus.SUSPENDED)
                .build();

        given(userRepository.findByEmail("alice.smith@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("StrongPassword1", "$2a$10$hashedPassword")).willReturn(true);

        assertThatThrownBy(() -> authService.login(validLoginRequest))
                .isInstanceOf(AccountStatusException.class)
                .hasMessageContaining("suspended");

        verify(jwtService, never()).generateToken(any(), any(), any());
    }

    @Test
    @DisplayName("login rejects DEACTIVATED account with 403 after password matches")
    void testLogin_deactivated_throws403() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("alice.smith@example.com")
                .passwordHash("$2a$10$hashedPassword")
                .accountStatus(AccountStatus.DEACTIVATED)
                .build();

        given(userRepository.findByEmail("alice.smith@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("StrongPassword1", "$2a$10$hashedPassword")).willReturn(true);

        assertThatThrownBy(() -> authService.login(validLoginRequest))
                .isInstanceOf(AccountStatusException.class)
                .hasMessageContaining("deactivated");

        verify(jwtService, never()).generateToken(any(), any(), any());
    }

    @Test
    @DisplayName("login rejects REJECTED account with 403 after password matches")
    void testLogin_rejected_throws403() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("alice.smith@example.com")
                .passwordHash("$2a$10$hashedPassword")
                .accountStatus(AccountStatus.REJECTED)
                .build();

        given(userRepository.findByEmail("alice.smith@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("StrongPassword1", "$2a$10$hashedPassword")).willReturn(true);

        assertThatThrownBy(() -> authService.login(validLoginRequest))
                .isInstanceOf(AccountStatusException.class)
                .hasMessageContaining("rejected");

        verify(jwtService, never()).generateToken(any(), any(), any());
    }

    @Test
    @DisplayName("login verifies password BEFORE checking status to prevent email enumeration")
    void testLogin_wrongPasswordOnPendingAccount_returnsGeneric401Not403() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("alice.smith@example.com")
                .passwordHash("$2a$10$hashedPassword")
                .accountStatus(AccountStatus.PENDING_VERIFICATION)
                .build();

        given(userRepository.findByEmail("alice.smith@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("StrongPassword1", "$2a$10$hashedPassword")).willReturn(false);

        // Crucial security test: Must throw InvalidCredentialsException (401), NOT AccountStatusException (403)!
        assertThatThrownBy(() -> authService.login(validLoginRequest))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password.");
    }

    @Test
    @DisplayName("login: 5 consecutive failures triggers lockout, setting lockedUntil 15 minutes in future")
    void testLogin_fiveConsecutiveFailures_triggersLockout() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("alice.smith@example.com")
                .passwordHash("$2a$10$hashedPassword")
                .accountStatus(AccountStatus.ACTIVE)
                .failedAttemptCount(4)
                .lockedUntil(null)
                .build();

        given(userRepository.findByEmail("alice.smith@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("StrongPassword1", "$2a$10$hashedPassword")).willReturn(false);

        Instant before = Instant.now().plus(Duration.ofMinutes(14)).plus(Duration.ofSeconds(50));

        assertThatThrownBy(() -> authService.login(validLoginRequest))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password.");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User savedUser = captor.getValue();

        assertThat(savedUser.getFailedAttemptCount()).isEqualTo(5);
        assertThat(savedUser.getLockedUntil()).isNotNull();
        assertThat(savedUser.getLockedUntil()).isAfter(before);
        assertThat(savedUser.isAccountLocked()).isTrue();
    }

    @Test
    @DisplayName("login: attempt while locked (even with correct password) is rejected with 423 without verifying password")
    void testLogin_attemptWhileLocked_rejectedWith423WithoutCheckingPassword() {
        UUID userId = UUID.randomUUID();
        Instant lockoutUntil = Instant.now().plus(Duration.ofMinutes(10));
        User lockedUser = User.builder()
                .id(userId)
                .email("alice.smith@example.com")
                .passwordHash("$2a$10$hashedPassword")
                .accountStatus(AccountStatus.ACTIVE)
                .failedAttemptCount(5)
                .lockedUntil(lockoutUntil)
                .build();

        given(userRepository.findByEmail("alice.smith@example.com")).willReturn(Optional.of(lockedUser));

        assertThatThrownBy(() -> authService.login(validLoginRequest))
                .isInstanceOf(AccountLockedException.class)
                .hasMessageContaining("Account is temporarily locked")
                .hasMessageContaining(lockoutUntil.toString());

        // Password matching must NOT be attempted while locked
        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtService, never()).generateToken(any(), any(), any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("login: after lockedUntil passes, correct login succeeds, resets counter and clears lockedUntil")
    void testLogin_afterLockedUntilPasses_correctLoginSucceedsAndResetsLockout() {
        UUID userId = UUID.randomUUID();
        Instant pastLockout = Instant.now().minus(Duration.ofMinutes(1));
        User previouslyLockedUser = User.builder()
                .id(userId)
                .email("alice.smith@example.com")
                .passwordHash("$2a$10$hashedPassword")
                .accountStatus(AccountStatus.ACTIVE)
                .failedAttemptCount(5)
                .lockedUntil(pastLockout)
                .build();
        Role role = Role.builder().name("RESIDENT").build();
        previouslyLockedUser.addRole(role);

        given(userRepository.findByEmail("alice.smith@example.com")).willReturn(Optional.of(previouslyLockedUser));
        given(passwordEncoder.matches("StrongPassword1", "$2a$10$hashedPassword")).willReturn(true);
        given(jwtService.generateToken(eq(userId), eq("alice.smith@example.com"), any())).willReturn("mock.jwt.token");
        given(jwtService.getExpirationSeconds()).willReturn(1800L);

        LoginResponse response = authService.login(validLoginRequest);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("mock.jwt.token");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User savedUser = captor.getValue();

        assertThat(savedUser.getFailedAttemptCount()).isZero();
        assertThat(savedUser.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("login: successful login before reaching 5 failures resets counter back to 0")
    void testLogin_successfulLoginBeforeFiveFailures_resetsCounterToZero() {
        UUID userId = UUID.randomUUID();
        User userWithFailedAttempts = User.builder()
                .id(userId)
                .email("alice.smith@example.com")
                .passwordHash("$2a$10$hashedPassword")
                .accountStatus(AccountStatus.ACTIVE)
                .failedAttemptCount(3)
                .lockedUntil(null)
                .build();
        Role role = Role.builder().name("RESIDENT").build();
        userWithFailedAttempts.addRole(role);

        given(userRepository.findByEmail("alice.smith@example.com")).willReturn(Optional.of(userWithFailedAttempts));
        given(passwordEncoder.matches("StrongPassword1", "$2a$10$hashedPassword")).willReturn(true);
        given(jwtService.generateToken(eq(userId), eq("alice.smith@example.com"), any())).willReturn("mock.jwt.token");
        given(jwtService.getExpirationSeconds()).willReturn(1800L);

        LoginResponse response = authService.login(validLoginRequest);

        assertThat(response).isNotNull();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User savedUser = captor.getValue();

        assertThat(savedUser.getFailedAttemptCount()).isZero();
        assertThat(savedUser.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("login: unknown-email attempts never touch or reveal lockout state")
    void testLogin_unknownEmail_neverTouchesOrRevealsLockoutState() {
        given(userRepository.findByEmail("alice.smith@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(validLoginRequest))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password.");

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtService, never()).generateToken(any(), any(), any());
    }

    @Test
    @DisplayName("login: failed attempt under 5 increments counter without locking")
    void testLogin_failedAttemptUnderFive_incrementsCounterWithoutLocking() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("alice.smith@example.com")
                .passwordHash("$2a$10$hashedPassword")
                .accountStatus(AccountStatus.ACTIVE)
                .failedAttemptCount(2)
                .lockedUntil(null)
                .build();

        given(userRepository.findByEmail("alice.smith@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("StrongPassword1", "$2a$10$hashedPassword")).willReturn(false);

        assertThatThrownBy(() -> authService.login(validLoginRequest))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password.");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User savedUser = captor.getValue();

        assertThat(savedUser.getFailedAttemptCount()).isEqualTo(3);
        assertThat(savedUser.getLockedUntil()).isNull();
        assertThat(savedUser.isAccountLocked()).isFalse();
    }

    @Test
    @DisplayName("login: failed attempt reaching exactly 4 (boundary) increments counter to 4 and does NOT lock account")
    void testLogin_failedAttemptExactlyFour_doesNotLockAccount() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("alice.smith@example.com")
                .passwordHash("$2a$10$hashedPassword")
                .accountStatus(AccountStatus.ACTIVE)
                .failedAttemptCount(3)
                .lockedUntil(null)
                .build();

        given(userRepository.findByEmail("alice.smith@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("StrongPassword1", "$2a$10$hashedPassword")).willReturn(false);

        assertThatThrownBy(() -> authService.login(validLoginRequest))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password.");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User savedUser = captor.getValue();

        assertThat(savedUser.getFailedAttemptCount()).isEqualTo(4);
        assertThat(savedUser.getLockedUntil()).isNull();
        assertThat(savedUser.isAccountLocked()).isFalse();
    }

    @Test
    @DisplayName("register: email with leading/trailing whitespace and uppercase is trimmed and normalized to lowercase")
    void testRegister_emailWithUppercaseAndWhitespace_isTrimmedAndNormalized() {
        RegisterRequest request = RegisterRequest.builder()
                .firstName(" Alice ")
                .lastName(" Smith ")
                .email("  ALICE.SMITH@EXAMPLE.COM  ")
                .phone(" +94771234567 ")
                .requestedRole("OWNER")
                .password("SecurePass1")
                .confirmPassword("SecurePass1")
                .build();

        given(userRepository.existsByEmail("alice.smith@example.com")).willReturn(false);
        given(passwordEncoder.encode("SecurePass1")).willReturn("hashed-password");
        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        RegisterResponse response = authService.register(request);

        assertThat(response.getEmail()).isEqualTo("alice.smith@example.com");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User savedUser = captor.getValue();

        assertThat(savedUser.getEmail()).isEqualTo("alice.smith@example.com");
        assertThat(savedUser.getUsername()).isEqualTo("alice.smith@example.com");
        assertThat(savedUser.getFirstName()).isEqualTo("Alice");
        assertThat(savedUser.getLastName()).isEqualTo("Smith");
        assertThat(savedUser.getPhone()).isEqualTo("+94771234567");
        assertThat(savedUser.getRequestedRole()).isEqualTo("OWNER");
    }

    @Test
    @DisplayName("register: duplicate email check is case-insensitive and rejects uppercase duplicate")
    void testRegister_caseInsensitiveDuplicateEmail_throwsDuplicateEmailException() {
        RegisterRequest request = RegisterRequest.builder()
                .firstName("Alice")
                .lastName("Smith")
                .email("ALICE.SMITH@EXAMPLE.COM")
                .phone("+94771234567")
                .requestedRole("OWNER")
                .password("SecurePass1")
                .confirmPassword("SecurePass1")
                .build();

        given(userRepository.existsByEmail("alice.smith@example.com")).willReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessage("Email already in use");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("login: email lookup is case-insensitive and trims whitespace")
    void testLogin_emailWithMixedCaseAndWhitespace_authenticatesSuccessfully() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("alice.smith@example.com")
                .passwordHash("$2a$10$hashedPassword")
                .accountStatus(AccountStatus.ACTIVE)
                .failedAttemptCount(0)
                .build();
        Role role = Role.builder().name("RESIDENT").build();
        user.addRole(role);

        LoginRequest mixedCaseRequest = LoginRequest.builder()
                .email("  ALICE.SMITH@EXAMPLE.COM  ")
                .password("StrongPassword1")
                .build();

        given(userRepository.findByEmail("alice.smith@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("StrongPassword1", "$2a$10$hashedPassword")).willReturn(true);
        given(jwtService.generateToken(eq(userId), eq("alice.smith@example.com"), any())).willReturn("mock.jwt.token");
        given(jwtService.getExpirationSeconds()).willReturn(1800L);

        LoginResponse response = authService.login(mixedCaseRequest);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("mock.jwt.token");
        assertThat(response.getUser().getEmail()).isEqualTo("alice.smith@example.com");
    }
}

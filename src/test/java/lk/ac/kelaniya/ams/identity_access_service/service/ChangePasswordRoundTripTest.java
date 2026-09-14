package lk.ac.kelaniya.ams.identity_access_service.service;

import lk.ac.kelaniya.ams.identity_access_service.dto.request.ChangePasswordRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.request.LoginRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.LoginResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lk.ac.kelaniya.ams.identity_access_service.entity.Role;
import lk.ac.kelaniya.ams.identity_access_service.entity.User;
import lk.ac.kelaniya.ams.identity_access_service.exception.InvalidCredentialsException;
import lk.ac.kelaniya.ams.identity_access_service.repository.UserRepository;
import lk.ac.kelaniya.ams.identity_access_service.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * End-to-end round-trip test verifying the complete password change lifecycle (AMS1-S2-IAM-01).
 * Validates that:
 * 1. An account (e.g. admin-created with mustChangePassword=true) logs in with its initial password.
 * 2. Authenticated user changes password via UserService with real BCrypt hashing.
 * 3. mustChangePassword successfully flips from true to false (closing the IAM-05 lifecycle loop).
 * 4. Subsequent login with the OLD password fails with 401 (InvalidCredentialsException).
 * 5. Subsequent login with the NEW password succeeds with mustChangePassword=false.
 */
@ExtendWith(MockitoExtension.class)
class ChangePasswordRoundTripTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;
    private UserService userService;

    private Map<UUID, User> userDb;
    private Map<String, User> userEmailDb;

    private UUID userId;
    private String userEmail;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
        userService = new UserService(userRepository, passwordEncoder);

        userDb = new HashMap<>();
        userEmailDb = new HashMap<>();

        given(userRepository.findById(any(UUID.class)))
                .willAnswer(invocation -> Optional.ofNullable(userDb.get(invocation.getArgument(0))));

        given(userRepository.findByEmail(any(String.class)))
                .willAnswer(invocation -> Optional.ofNullable(userEmailDb.get(invocation.getArgument(0))));

        given(userRepository.save(any(User.class)))
                .willAnswer(invocation -> {
                    User saved = invocation.getArgument(0);
                    userDb.put(saved.getId(), saved);
                    userEmailDb.put(saved.getEmail().toLowerCase(), saved);
                    return saved;
                });

        userId = UUID.randomUUID();
        userEmail = "resident.alice@ams.lk";

        Role residentRole = Role.builder()
                .id(UUID.randomUUID())
                .name("RESIDENT")
                .description("Resident Role")
                .build();

        // Account provisioned (e.g. by admin with temporary password and mustChangePassword=true)
        User initialUser = User.builder()
                .id(userId)
                .email(userEmail)
                .username(userEmail)
                .passwordHash(passwordEncoder.encode("InitialTempPass2026"))
                .firstName("Alice")
                .lastName("Walker")
                .accountStatus(AccountStatus.ACTIVE)
                .mustChangePassword(true)
                .failedAttemptCount(0)
                .userRoles(new HashSet<>())
                .build();
        initialUser.addRole(residentRole);

        userDb.put(userId, initialUser);
        userEmailDb.put(userEmail.toLowerCase(), initialUser);
    }

    @Test
    @DisplayName("Round-trip test: initial login (mustChangePassword=true) -> change password -> mustChangePassword flips to false -> old password login fails -> new password login succeeds")
    void testChangePassword_fullRoundTripLifecycle() {
        given(jwtService.generateToken(eq(userId), eq(userEmail), any()))
                .willReturn("jwt.roundtrip.token");
        given(jwtService.getExpirationSeconds()).willReturn(3600L);

        // Phase 1: Login with initial temporary password
        LoginRequest initialLoginRequest = LoginRequest.builder()
                .email(userEmail)
                .password("InitialTempPass2026")
                .build();

        LoginResponse initialLoginResponse = authService.login(initialLoginRequest);

        assertThat(initialLoginResponse).isNotNull();
        assertThat(initialLoginResponse.getAccessToken()).isEqualTo("jwt.roundtrip.token");
        assertThat(initialLoginResponse.isMustChangePassword()).isTrue();
        assertThat(initialLoginResponse.getUser().isMustChangePassword()).isTrue();

        // Phase 2: Authenticated user changes password via PUT /api/v1/users/me/password
        ChangePasswordRequest changePasswordRequest = ChangePasswordRequest.builder()
                .currentPassword("InitialTempPass2026")
                .newPassword("BrandNewSecurePass999")
                .confirmNewPassword("BrandNewSecurePass999")
                .build();

        userService.changePassword(userId, changePasswordRequest);

        // Verify state changes on persisted entity
        User updatedUser = userDb.get(userId);
        assertThat(updatedUser).isNotNull();
        // Critical regression verification: mustChangePassword must flip to false
        assertThat(updatedUser.isMustChangePassword()).isFalse();
        // Real BCrypt hash must match the new password and reject the old password
        assertThat(passwordEncoder.matches("BrandNewSecurePass999", updatedUser.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("InitialTempPass2026", updatedUser.getPasswordHash())).isFalse();
        // Other attributes must remain intact
        assertThat(updatedUser.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(updatedUser.getFailedAttemptCount()).isZero();

        // Phase 3: Confirm subsequent login with OLD password FAILS
        LoginRequest oldPasswordLoginRequest = LoginRequest.builder()
                .email(userEmail)
                .password("InitialTempPass2026")
                .build();

        assertThatThrownBy(() -> authService.login(oldPasswordLoginRequest))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password.");

        // Phase 4: Confirm subsequent login with NEW password SUCCEEDS
        LoginRequest newPasswordLoginRequest = LoginRequest.builder()
                .email(userEmail)
                .password("BrandNewSecurePass999")
                .build();

        LoginResponse newLoginResponse = authService.login(newPasswordLoginRequest);

        assertThat(newLoginResponse).isNotNull();
        assertThat(newLoginResponse.getAccessToken()).isEqualTo("jwt.roundtrip.token");
        // Confirms mustChangePassword is now false on login response
        assertThat(newLoginResponse.isMustChangePassword()).isFalse();
        assertThat(newLoginResponse.getUser().isMustChangePassword()).isFalse();
    }
}

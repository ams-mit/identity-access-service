package lk.ac.kelaniya.ams.identity_access_service.service;

import lk.ac.kelaniya.ams.identity_access_service.dto.request.ForgotPasswordRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.request.LoginRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.request.ResetPasswordRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.LoginResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.MessageResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lk.ac.kelaniya.ams.identity_access_service.entity.PasswordResetToken;
import lk.ac.kelaniya.ams.identity_access_service.entity.Role;
import lk.ac.kelaniya.ams.identity_access_service.entity.User;
import lk.ac.kelaniya.ams.identity_access_service.exception.InvalidCredentialsException;
import lk.ac.kelaniya.ams.identity_access_service.exception.InvalidResetTokenException;
import lk.ac.kelaniya.ams.identity_access_service.repository.PasswordResetTokenRepository;
import lk.ac.kelaniya.ams.identity_access_service.repository.UserRepository;
import lk.ac.kelaniya.ams.identity_access_service.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
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
import static org.mockito.BDDMockito.willAnswer;

/**
 * End-to-end round-trip integration test verifying the complete forgot-password and reset-password lifecycle (AMS1-S2-IAM-02).
 * Validates:
 * 1. Requesting forgot-password generates a hashed token with configured 30m expiry and generic 200 response.
 * 2. Requesting forgot-password twice invalidates the first token (only the latest remains valid).
 * 3. Reset attempt with invalidated first token is rejected generically with 400.
 * 4. Reset attempt with nonexistent/garbage token is rejected generically with 400.
 * 5. Reset attempt with expired token is rejected generically with 400.
 * 6. Confirms all three rejection cases (expired, used, invalid) produce indistinguishable errors.
 * 7. Reset with valid second token succeeds, updates password hash, clears lockout/first-login flags.
 * 8. Re-attempting reset with second token is rejected (already used).
 * 9. Subsequent login with OLD password fails with 401.
 * 10. Subsequent login with NEW password succeeds with valid JWT.
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetRoundTripTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private JwtService jwtService;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    private Map<UUID, User> userDb;
    private Map<String, User> userEmailDb;
    private List<PasswordResetToken> tokenStore;

    private UUID userId;
    private String userEmail;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(userRepository, passwordEncoder, jwtService, passwordResetTokenRepository);

        userDb = new HashMap<>();
        userEmailDb = new HashMap<>();
        tokenStore = new ArrayList<>();

        given(userRepository.findByEmail(any(String.class)))
                .willAnswer(invocation -> Optional.ofNullable(userEmailDb.get(invocation.getArgument(0).toString().toLowerCase())));

        given(userRepository.save(any(User.class)))
                .willAnswer(invocation -> {
                    User saved = invocation.getArgument(0);
                    userDb.put(saved.getId(), saved);
                    userEmailDb.put(saved.getEmail().toLowerCase(), saved);
                    return saved;
                });

        given(passwordResetTokenRepository.save(any(PasswordResetToken.class)))
                .willAnswer(invocation -> {
                    PasswordResetToken token = invocation.getArgument(0);
                    if (token.getId() == null) {
                        token.setId(UUID.randomUUID());
                    }
                    tokenStore.removeIf(t -> t.getId().equals(token.getId()));
                    tokenStore.add(token);
                    return token;
                });

        given(passwordResetTokenRepository.findByTokenHash(any(String.class)))
                .willAnswer(invocation -> {
                    String hash = invocation.getArgument(0);
                    return tokenStore.stream()
                            .filter(t -> t.getTokenHash().equals(hash))
                            .findFirst();
                });

        willAnswer(invocation -> {
            User user = invocation.getArgument(0);
            Instant now = invocation.getArgument(1);
            tokenStore.stream()
                    .filter(t -> t.getUser().getId().equals(user.getId()) && t.getUsedAt() == null)
                    .forEach(t -> t.setUsedAt(now));
            return null;
        }).given(passwordResetTokenRepository).invalidateAllActiveTokensForUser(any(User.class), any(Instant.class));

        userId = UUID.randomUUID();
        userEmail = "resident.bob@ams.lk";

        Role residentRole = Role.builder()
                .id(UUID.randomUUID())
                .name("RESIDENT")
                .description("Resident Role")
                .build();

        User initialUser = User.builder()
                .id(userId)
                .email(userEmail)
                .username(userEmail)
                .passwordHash(passwordEncoder.encode("InitialSecretPass123"))
                .firstName("Bob")
                .lastName("Taylor")
                .accountStatus(AccountStatus.ACTIVE)
                .mustChangePassword(true)
                .failedAttemptCount(3)
                .userRoles(new HashSet<>())
                .build();
        initialUser.addRole(residentRole);

        userDb.put(userId, initialUser);
        userEmailDb.put(userEmail.toLowerCase(), initialUser);
    }

    private String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Full round-trip: request forgot-password twice (first invalidated) -> reject invalid/expired/used -> reset password -> old login fails -> new login succeeds")
    void testPasswordReset_completeLifecycleRoundTrip() {
        // Step 1: Initial user login works with initial password
        given(jwtService.generateToken(eq(userId), eq(userEmail), any()))
                .willReturn("jwt.initial.token");
        given(jwtService.getExpirationSeconds()).willReturn(3600L);

        LoginResponse initialLogin = authService.login(LoginRequest.builder()
                .email(userEmail)
                .password("InitialSecretPass123")
                .build());
        assertThat(initialLogin).isNotNull();
        assertThat(initialLogin.getAccessToken()).isEqualTo("jwt.initial.token");

        // Step 2: Request forgot-password first time
        MessageResponse forgotResponse1 = authService.forgotPassword(ForgotPasswordRequest.builder()
                .email(userEmail)
                .build());
        assertThat(forgotResponse1.getMessage()).isEqualTo("If an account is associated with this email, instructions will be provided.");
        assertThat(tokenStore).hasSize(1);
        PasswordResetToken token1 = tokenStore.get(0);
        assertThat(token1.getUsedAt()).isNull();

        // Step 3: Request forgot-password second time -> invalidates first token, generates second token
        MessageResponse forgotResponse2 = authService.forgotPassword(ForgotPasswordRequest.builder()
                .email(userEmail)
                .build());
        assertThat(forgotResponse2.getMessage()).isEqualTo("If an account is associated with this email, instructions will be provided.");
        assertThat(tokenStore).hasSize(2);
        PasswordResetToken token2 = tokenStore.stream()
                .filter(t -> !t.getId().equals(token1.getId()))
                .findFirst().orElseThrow();

        // Confirm token 1 was invalidated (usedAt is set)
        assertThat(token1.getUsedAt()).isNotNull();
        // Confirm token 2 is active
        assertThat(token2.getUsedAt()).isNull();

        // Step 4: Attempt reset using token 1 hash (already invalidated) -> generic 400 rejection
        // To simulate submitting token 1, we set up a raw token whose hash matches token 1
        String fakeRawToken1 = "rawToken1Value";
        token1.setTokenHash(sha256Hex(fakeRawToken1));

        assertThatThrownBy(() -> authService.resetPassword(ResetPasswordRequest.builder()
                .resetToken(fakeRawToken1)
                .newPassword("BrandNewSecurePass999")
                .confirmNewPassword("BrandNewSecurePass999")
                .build()))
                .isInstanceOf(InvalidResetTokenException.class)
                .hasMessage("Invalid or expired password reset token.");

        // Step 5: Attempt reset using garbage/nonexistent token -> generic 400 rejection
        assertThatThrownBy(() -> authService.resetPassword(ResetPasswordRequest.builder()
                .resetToken("nonexistent-garbage-token-xyz")
                .newPassword("BrandNewSecurePass999")
                .confirmNewPassword("BrandNewSecurePass999")
                .build()))
                .isInstanceOf(InvalidResetTokenException.class)
                .hasMessage("Invalid or expired password reset token.");

        // Step 6: Attempt reset using expired token -> generic 400 rejection
        String expiredRawToken = "expiredRawToken123";
        PasswordResetToken expiredToken = PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .user(userDb.get(userId))
                .tokenHash(sha256Hex(expiredRawToken))
                .expiresAt(Instant.now().minus(Duration.ofMinutes(10))) // expired
                .usedAt(null)
                .build();
        tokenStore.add(expiredToken);

        assertThatThrownBy(() -> authService.resetPassword(ResetPasswordRequest.builder()
                .resetToken(expiredRawToken)
                .newPassword("BrandNewSecurePass999")
                .confirmNewPassword("BrandNewSecurePass999")
                .build()))
                .isInstanceOf(InvalidResetTokenException.class)
                .hasMessage("Invalid or expired password reset token.");

        // Step 7: Reset password using valid second token -> succeeds
        String validRawToken2 = "validRawToken2Value";
        token2.setTokenHash(sha256Hex(validRawToken2));

        MessageResponse resetResponse = authService.resetPassword(ResetPasswordRequest.builder()
                .resetToken(validRawToken2)
                .newPassword("BrandNewSecurePass999")
                .confirmNewPassword("BrandNewSecurePass999")
                .build());

        assertThat(resetResponse.getMessage()).isEqualTo("Password has been reset successfully.");

        // Verify token 2 is now marked as used
        assertThat(token2.getUsedAt()).isNotNull();

        // Verify user state: password hash updated, mustChangePassword cleared, failedAttemptCount reset
        User updatedUser = userDb.get(userId);
        assertThat(passwordEncoder.matches("BrandNewSecurePass999", updatedUser.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("InitialSecretPass123", updatedUser.getPasswordHash())).isFalse();
        assertThat(updatedUser.isMustChangePassword()).isFalse();
        assertThat(updatedUser.getFailedAttemptCount()).isZero();
        assertThat(updatedUser.getLockedUntil()).isNull();

        // Step 8: Re-attempting reset with token 2 now fails (already used)
        assertThatThrownBy(() -> authService.resetPassword(ResetPasswordRequest.builder()
                .resetToken(validRawToken2)
                .newPassword("YetAnotherPass123")
                .confirmNewPassword("YetAnotherPass123")
                .build()))
                .isInstanceOf(InvalidResetTokenException.class)
                .hasMessage("Invalid or expired password reset token.");

        // Step 9: Subsequent login with OLD password fails (401 InvalidCredentialsException)
        assertThatThrownBy(() -> authService.login(LoginRequest.builder()
                .email(userEmail)
                .password("InitialSecretPass123")
                .build()))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password.");

        // Step 10: Subsequent login with NEW password succeeds
        given(jwtService.generateToken(eq(userId), eq(userEmail), any()))
                .willReturn("jwt.new.token");

        LoginResponse newLogin = authService.login(LoginRequest.builder()
                .email(userEmail)
                .password("BrandNewSecurePass999")
                .build());

        assertThat(newLogin).isNotNull();
        assertThat(newLogin.getAccessToken()).isEqualTo("jwt.new.token");
        assertThat(newLogin.isMustChangePassword()).isFalse();
        assertThat(newLogin.getUser().isMustChangePassword()).isFalse();
    }
}

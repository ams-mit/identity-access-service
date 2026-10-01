package kln.ams.identityaccess.service;

import kln.ams.identityaccess.dto.auth.AuthenticatedUserResponse;
import kln.ams.identityaccess.dto.auth.ChangePasswordRequest;
import kln.ams.identityaccess.dto.auth.ForgotPasswordRequest;
import kln.ams.identityaccess.dto.auth.LoginRequest;
import kln.ams.identityaccess.dto.auth.LoginResponse;
import kln.ams.identityaccess.dto.auth.LoginUserDto;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    public static final String FORGOT_PASSWORD_GENERIC_MESSAGE =
            "If an account is associated with this email, instructions will be provided.";
    public static final String RESET_PASSWORD_SUCCESS_MESSAGE =
            "Password has been reset successfully.";
    public static final String DEFAULT_CANONICAL_ROLE = "TENANT_RESIDENT";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Environment environment;

    @Value("${auth.password-reset.token-validity-minutes:15}")
    private long tokenValidityMinutes = 15;

    public void setTokenValidityMinutes(long tokenValidityMinutes) {
        this.tokenValidityMinutes = tokenValidityMinutes;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String identifier = request.getUsername().trim();

        User user = userRepository.findByUsernameIgnoreCase(identifier)
                .or(() -> userRepository.findByEmailIgnoreCase(identifier))
                .orElseThrow(() -> {
                    log.warn("Login attempt for non-existent user/email: {}", identifier);
                    return new InvalidCredentialsException();
                });

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Login failed: invalid password for user '{}'", user.getUsername());
            throw new InvalidCredentialsException();
        }

        if (user.getAccountStatus() != AccountStatus.ACTIVE || user.isAccountLocked()) {
            log.warn("Login rejected: account '{}' has status '{}' or is locked", user.getUsername(), user.getAccountStatus());
            throw new AccountInactiveException("Account is inactive or suspended");
        }

        List<String> roles = user.getUserRoles() != null
                ? user.getUserRoles().stream()
                        .map(ur -> ur.getRole().getName())
                        .sorted()
                        .toList()
                : Collections.emptyList();

        String token = jwtService.generateToken(user.getId(), roles);
        log.info("Login successful for user '{}' (id: {})", user.getUsername(), user.getId());

        LoginUserDto userDto = LoginUserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .roles(roles)
                .status(user.getAccountStatus().name())
                .build();

        return LoginResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(1800)
                .user(userDto)
                .build();
    }

    @Transactional(readOnly = true)
    public AuthenticatedUserResponse getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new AccountInactiveException("Account is inactive or suspended");
        }

        List<String> roles = user.getUserRoles() != null
                ? user.getUserRoles().stream()
                        .map(ur -> ur.getRole().getName())
                        .sorted()
                        .toList()
                : Collections.emptyList();

        return AuthenticatedUserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .roles(roles)
                .status(user.getAccountStatus().name())
                .build();
    }

    /**
     * Self-registration (AUTH-004).
     * Creates new user with username = email, status = INACTIVE, and default canonical role TENANT_RESIDENT.
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            logStructured("USER_REGISTERED", "FAILED", null, "VALIDATION_ERROR", "Passwords do not match");
            throw new PasswordValidationException("Passwords do not match");
        }

        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String username = email;

        if (userRepository.existsByEmailIgnoreCase(email) || userRepository.existsByUsernameIgnoreCase(username)) {
            logStructured("USER_REGISTERED", "FAILED", null, "USER_ALREADY_EXISTS", "User already exists with email: " + email);
            throw new UserAlreadyExistsException("User already exists with this email or username");
        }

        Role defaultRole = roleRepository.findByNameIgnoreCase(DEFAULT_CANONICAL_ROLE)
                .orElseThrow(() -> new IllegalStateException("Default role " + DEFAULT_CANONICAL_ROLE + " not found"));

        User user = User.builder()
                .username(username)
                .email(email)
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .phone(request.getPhone() != null ? request.getPhone().trim() : null)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .accountStatus(AccountStatus.INACTIVE)
                .failedAttemptCount(0)
                .build();

        user.addRole(defaultRole);
        User savedUser = userRepository.save(user);

        logStructured("USER_REGISTERED", "SUCCESS", savedUser.getId(), null, "User registered successfully with status INACTIVE");

        return RegisterResponse.builder()
                .id(savedUser.getId())
                .username(savedUser.getUsername())
                .email(savedUser.getEmail())
                .firstName(savedUser.getFirstName())
                .lastName(savedUser.getLastName())
                .phone(savedUser.getPhone())
                .status(savedUser.getAccountStatus().name())
                .roles(List.of(defaultRole.getName()))
                .createdAt(savedUser.getCreatedAt())
                .build();
    }

    /**
     * Initiates password reset flow (AUTH-005).
     * Enforces anti-enumeration: always returns HTTP 200 with identical generic message.
     */
    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        Optional<User> userOptional = userRepository.findByEmailIgnoreCase(email);

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            if (user.getAccountStatus() == AccountStatus.ACTIVE) {
                passwordResetTokenRepository.invalidateAllActiveTokensForUser(user, Instant.now());

                String rawToken = generateSecureToken();
                String tokenHash = hashToken(rawToken);
                Instant expiresAt = Instant.now().plus(Duration.ofMinutes(tokenValidityMinutes));

                PasswordResetToken resetToken = PasswordResetToken.builder()
                        .user(user)
                        .tokenHash(tokenHash)
                        .expiresAt(expiresAt)
                        .build();

                passwordResetTokenRepository.save(resetToken);

                // Token value logged ONLY in dev profile for testing per JWT security standard
                if (environment != null && environment.acceptsProfiles(Profiles.of("dev", "local"))) {
                    log.info("[DEV-ONLY] Password reset token generated for email {}: {}", email, rawToken);
                }

                logStructured("PASSWORD_RESET_REQUESTED", "SUCCESS", user.getId(), null, "Password reset token generated");
            } else {
                logStructured("PASSWORD_RESET_REQUESTED", "IGNORED", user.getId(), null, "Password reset ignored for non-active user (status: " + user.getAccountStatus() + ")");
            }
        } else {
            logStructured("PASSWORD_RESET_REQUESTED", "IGNORED", null, null, "Password reset ignored for non-existent email");
        }

        return MessageResponse.builder()
                .message(FORGOT_PASSWORD_GENERIC_MESSAGE)
                .build();
    }

    /**
     * Completes password reset flow using token (AUTH-006).
     * Enforces anti-enumeration: all invalid, expired, already-used token cases return identical 400 INVALID_RESET_TOKEN.
     */
    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
            logStructured("PASSWORD_RESET_COMPLETED", "FAILED", null, "VALIDATION_ERROR", "New password and confirm password do not match");
            throw new PasswordValidationException("New password and confirm password do not match");
        }

        String tokenHash = hashToken(request.getResetToken());
        Optional<PasswordResetToken> tokenOptional = passwordResetTokenRepository.findByTokenHash(tokenHash);

        if (tokenOptional.isEmpty()) {
            logStructured("PASSWORD_RESET_COMPLETED", "FAILED", null, "INVALID_RESET_TOKEN", "Reset token not found");
            throw new InvalidResetTokenException();
        }

        PasswordResetToken token = tokenOptional.get();

        if (token.isUsed() || token.isExpired()) {
            logStructured("PASSWORD_RESET_COMPLETED", "FAILED", token.getUser() != null ? token.getUser().getId() : null, "INVALID_RESET_TOKEN", "Reset token is used or expired");
            throw new InvalidResetTokenException();
        }

        User user = token.getUser();
        if (user == null || user.getAccountStatus() != AccountStatus.ACTIVE) {
            logStructured("PASSWORD_RESET_COMPLETED", "FAILED", user != null ? user.getId() : null, "INVALID_RESET_TOKEN", "User not active for password reset");
            throw new InvalidResetTokenException();
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setFailedAttemptCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        Instant now = Instant.now();
        token.setUsedAt(now);
        passwordResetTokenRepository.save(token);

        passwordResetTokenRepository.invalidateAllActiveTokensForUser(user, now);

        logStructured("PASSWORD_RESET_COMPLETED", "SUCCESS", user.getId(), null, "Password reset successfully via token");

        return MessageResponse.builder()
                .message(RESET_PASSWORD_SUCCESS_MESSAGE)
                .build();
    }

    /**
     * Self-service password change for authenticated user (AUTH-007).
     * Returns 204 on success.
     */
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("User not found or unauthenticated"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            logStructured("PASSWORD_CHANGED", "FAILED", userId, "INVALID_CREDENTIALS", "Current password does not match");
            throw new InvalidCredentialsException("Current password is incorrect", HttpStatus.BAD_REQUEST);
        }

        if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
            logStructured("PASSWORD_CHANGED", "FAILED", userId, "VALIDATION_ERROR", "New password and confirm password do not match");
            throw new PasswordValidationException("New password and confirm password do not match");
        }

        if (request.getNewPassword().equals(request.getCurrentPassword()) ||
                passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            logStructured("PASSWORD_CHANGED", "FAILED", userId, "VALIDATION_ERROR", "New password cannot be the same as current password");
            throw new PasswordValidationException("New password must differ from current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        logStructured("PASSWORD_CHANGED", "SUCCESS", userId, null, "Password changed successfully");
    }

    /**
     * Stateless logout (AUTH-003).
     */
    public void logout(UUID userId) {
        logStructured("LOGOUT", "SUCCESS", userId, null, "User logged out");
    }

    private void logStructured(String operation, String result, UUID userId, String errorCode, String message) {
        String requestId = MDC.get("requestId");
        String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        log.info("operation={} result={} service=identity-access-service requestId={} userId={} errorCode={} timestamp={} message=\"{}\"",
                operation, result, requestId != null ? requestId : "UNKNOWN",
                userId != null ? userId.toString() : "ANONYMOUS",
                errorCode != null ? errorCode : "NONE",
                timestamp, message);
    }

    private String generateSecureToken() {
        byte[] randomBytes = new byte[32]; // 256 bits of entropy
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }
}

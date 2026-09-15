package lk.ac.kelaniya.ams.identity_access_service.service;

import lk.ac.kelaniya.ams.identity_access_service.dto.request.ForgotPasswordRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.request.LoginRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.request.RegisterRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.request.ResetPasswordRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.LoginResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.MessageResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.RegisterResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lk.ac.kelaniya.ams.identity_access_service.entity.PasswordResetToken;
import lk.ac.kelaniya.ams.identity_access_service.entity.User;
import lk.ac.kelaniya.ams.identity_access_service.exception.AccountLockedException;
import lk.ac.kelaniya.ams.identity_access_service.exception.AccountStatusException;
import lk.ac.kelaniya.ams.identity_access_service.exception.DuplicateEmailException;
import lk.ac.kelaniya.ams.identity_access_service.exception.InvalidCredentialsException;
import lk.ac.kelaniya.ams.identity_access_service.exception.InvalidResetTokenException;
import lk.ac.kelaniya.ams.identity_access_service.exception.PasswordMismatchException;
import lk.ac.kelaniya.ams.identity_access_service.repository.PasswordResetTokenRepository;
import lk.ac.kelaniya.ams.identity_access_service.repository.UserRepository;
import lk.ac.kelaniya.ams.identity_access_service.security.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Service managing user registration, authentication, and password reset lifecycle.
 */
@Slf4j
@Service
public class AuthService {

    public static final String FORGOT_PASSWORD_GENERIC_MESSAGE =
            "If an account is associated with this email, instructions will be provided.";
    public static final String RESET_PASSWORD_SUCCESS_MESSAGE =
            "Password has been reset successfully.";
    public static final String GENERIC_INVALID_RESET_TOKEN_MESSAGE =
            "Invalid or expired password reset token.";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    @Value("${auth.password-reset.token-validity-minutes:30}")
    private long tokenValidityMinutes = 30;

    @Autowired
    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            PasswordResetTokenRepository passwordResetTokenRepository
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this(userRepository, passwordEncoder, jwtService, null);
    }

    public void setTokenValidityMinutes(long tokenValidityMinutes) {
        this.tokenValidityMinutes = tokenValidityMinutes;
    }


    /**
     * Registers a new user with PENDING_VERIFICATION status.
     * Prevents privilege escalation and verifies email uniqueness.
     *
     * @param request registration details
     * @return registration response with user ID and email
     * @throws PasswordMismatchException if password and confirmPassword do not match
     * @throws DuplicateEmailException   if email is already registered
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new PasswordMismatchException("Passwords do not match");
        }

        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            log.warn("Registration rejected: duplicate email address attempt");
            throw new DuplicateEmailException("Email already in use");
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());

        // Advisory only - requestedRole records the applicant's intended role for administrative
        // review. It does NOT grant any access, system privileges, or UserRole mappings upon registration.
        User user = User.builder()
                .email(email)
                .username(email)
                .passwordHash(passwordHash)
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .phone(request.getPhone() != null ? request.getPhone().trim() : null)
                .requestedRole(request.getRequestedRole())
                .accountStatus(AccountStatus.PENDING_VERIFICATION)
                .failedAttemptCount(0)
                .build();

        User savedUser = userRepository.save(user);

        log.info("User registered successfully with id: {}", savedUser.getId());

        return RegisterResponse.builder()
                .userId(savedUser.getId())
                .email(savedUser.getEmail())
                .accountStatus(savedUser.getAccountStatus())
                .requestedRole(savedUser.getRequestedRole())
                .build();
    }

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    /**
     * Authenticates user credentials, verifies account lifecycle status, and issues an RS256-signed JWT.
     * Enforces anti-enumeration: user-not-found and wrong password both produce identical generic 401 errors.
     * Enforces failed-login lockout: 5 consecutive failures locks account for 15 minutes (HTTP 423).
     * While locked, password verification is bypassed. Successful login clears lockout and resets failed attempts.
     *
     * @param request login credentials payload
     * @return authentication response containing RS256 JWT, expiry in seconds, and user details
     * @throws InvalidCredentialsException if email is not found or password does not match (generic 401)
     * @throws AccountLockedException      if account is temporarily locked (423)
     * @throws AccountStatusException      if account is PENDING_VERIFICATION, SUSPENDED, or DEACTIVATED (403)
     */
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public LoginResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isEmpty()) {
            log.warn("Authentication failed: user not found for email: {}", email);
            throw new InvalidCredentialsException("Invalid email or password.");
        }

        User user = userOptional.get();

        // 1. Check lockout status BEFORE verifying password to avoid wasted BCrypt computation
        if (user.isAccountLocked()) {
            log.warn("Authentication rejected: account locked until {} for user id: {}", user.getLockedUntil(), user.getId());
            throw new AccountLockedException("Account is temporarily locked. Try again after " + user.getLockedUntil() + ".", user.getLockedUntil());
        }

        // 2. Verify password FIRST before checking account status to prevent email enumeration
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            int attempts = user.getFailedAttemptCount() + 1;
            user.setFailedAttemptCount(attempts);
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(Instant.now().plus(LOCKOUT_DURATION));
                log.warn("Authentication failed: max attempts reached for user id: {}. Locked until {}",
                        user.getId(), user.getLockedUntil());
            } else {
                log.warn("Authentication failed: invalid password for user id: {}. Attempt {} of {}",
                        user.getId(), attempts, MAX_FAILED_ATTEMPTS);
            }
            userRepository.save(user);
            throw new InvalidCredentialsException("Invalid email or password.");
        }

        // 3. Check account status AFTER confirming credentials are valid
        AccountStatus status = user.getAccountStatus();
        if (status == AccountStatus.PENDING_VERIFICATION) {
            log.warn("Authentication rejected: account pending verification for user id: {}", user.getId());
            throw new AccountStatusException("ACCOUNT_PENDING_VERIFICATION", "Account is pending verification. Please verify your email before logging in.");
        } else if (status == AccountStatus.SUSPENDED) {
            log.warn("Authentication rejected: account suspended for user id: {}", user.getId());
            throw new AccountStatusException("ACCOUNT_SUSPENDED", "Account has been suspended. Please contact support.");
        } else if (status == AccountStatus.DEACTIVATED) {
            log.warn("Authentication rejected: account deactivated for user id: {}", user.getId());
            throw new AccountStatusException("ACCOUNT_DEACTIVATED", "Account has been deactivated. Please contact support.");
        } else if (status == AccountStatus.REJECTED) {
            log.warn("Authentication rejected: account rejected for user id: {}", user.getId());
            throw new AccountStatusException("ACCOUNT_REJECTED", "Account registration has been rejected. Please contact support.");
        } else if (status != AccountStatus.ACTIVE) {
            log.warn("Authentication rejected: non-active account status {} for user id: {}", status, user.getId());
            throw new AccountStatusException("ACCOUNT_INACTIVE", "Account is not active.");
        }

        // 4. Reset lockout and failed attempts on successful authentication
        user.setFailedAttemptCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        // 5. Extract roles (empty list if no roles assigned yet)
        List<String> roles = (user.getUserRoles() != null && !user.getUserRoles().isEmpty())
                ? user.getUserRoles().stream()
                    .map(ur -> ur.getRole() != null ? ur.getRole().getName() : null)
                    .filter(Objects::nonNull)
                    .toList()
                : List.of();

        // 6. Issue RS256 JWT
        String token = jwtService.generateToken(user.getId(), user.getEmail(), roles);
        long expiresIn = jwtService.getExpirationSeconds();

        log.info("User authenticated successfully with id: {}", user.getId());

        // Note: mustChangePassword is surfaced to notify frontend clients to force a password-change
        // screen on first login for admin-created accounts. Restricting/blocking access to other endpoints
        // until the password is changed is out of scope for IAM-05 and will be enforced as a follow-up once
        // IAM-01 (change password) is implemented.
        return LoginResponse.builder()
                .accessToken(token)
                .expiresIn(expiresIn)
                .mustChangePassword(user.isMustChangePassword())
                .user(LoginResponse.UserSummary.builder()
                        .userId(user.getId())
                        .email(user.getEmail())
                        .roles(roles)
                        .mustChangePassword(user.isMustChangePassword())
                        .build())
                .build();
    }

    /**
     * Initiates password reset for a given email address.
     * Enforces anti-enumeration: always returns the identical generic 200 message regardless of whether
     * the email exists, does not exist, or is in an inactive lifecycle state (REJECTED, DEACTIVATED, SUSPENDED).
     * Only ACTIVE accounts receive a persisted reset token hash and a server-side DEV-ONLY diagnostic log.
     * If a reset was previously requested, any outstanding active tokens for this user are invalidated.
     *
     * @param request forgot password payload containing email
     * @return generic informational message response
     */
    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            AccountStatus status = user.getAccountStatus();

            // Treat DEACTIVATED and non-active statuses consistently with login:
            // Only ACTIVE accounts can receive working password reset tokens.
            // A REJECTED, DEACTIVATED, or SUSPENDED registration must never receive a working reset link.
            if (status == AccountStatus.ACTIVE) {
                // Invalidate any existing active tokens for this user so only the latest is valid
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

                // Note: Actual email/SMS delivery is explicitly out of scope for this prototype (SRS).
                // DEV-ONLY: Log the raw token server-side for manual testing via Swagger/Postman without an SMTP server.
                // This diagnostic log would NOT exist in a production environment.
                log.info("[DEV-ONLY] Password reset token generated for email {}: {}", email, rawToken);
            } else {
                log.warn("Forgot password requested for non-active account (status: {}) with email: {}", status, email);
            }
        } else {
            log.info("Forgot password requested for nonexistent email: {}", email);
        }

        return MessageResponse.builder()
                .message(FORGOT_PASSWORD_GENERIC_MESSAGE)
                .build();
    }

    /**
     * Completes password reset using a reset token.
     * Validates token existence, expiry, and single-use constraints.
     * Enforces anti-enumeration: all invalid, expired, or already-used token cases return an identical generic 400.
     * Upon success, updates the password hash according to standard policy, clears mustChangePassword and lockout tracking,
     * marks the token as used, and invalidates any other outstanding reset tokens for the user.
     *
     * @param request reset password payload containing token, new password, and confirmation
     * @return success informational message response
     * @throws PasswordMismatchException if newPassword and confirmNewPassword do not match
     * @throws InvalidResetTokenException if token is missing, expired, already used, or user is ineligible
     */
    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
            log.warn("Password reset rejected: new password and confirmation do not match");
            throw new PasswordMismatchException("New password and confirm password do not match");
        }

        String tokenHash = hashToken(request.getResetToken());
        Optional<PasswordResetToken> tokenOptional = passwordResetTokenRepository.findByTokenHash(tokenHash);

        if (tokenOptional.isEmpty()) {
            log.warn("Password reset rejected: token not found");
            throw new InvalidResetTokenException(GENERIC_INVALID_RESET_TOKEN_MESSAGE);
        }

        PasswordResetToken token = tokenOptional.get();

        if (token.isUsed()) {
            log.warn("Password reset rejected: token already used at {}", token.getUsedAt());
            throw new InvalidResetTokenException(GENERIC_INVALID_RESET_TOKEN_MESSAGE);
        }

        if (token.isExpired()) {
            log.warn("Password reset rejected: token expired at {}", token.getExpiresAt());
            throw new InvalidResetTokenException(GENERIC_INVALID_RESET_TOKEN_MESSAGE);
        }

        User user = token.getUser();
        if (user == null || user.getAccountStatus() != AccountStatus.ACTIVE) {
            log.warn("Password reset rejected: user is null or not in ACTIVE status");
            throw new InvalidResetTokenException(GENERIC_INVALID_RESET_TOKEN_MESSAGE);
        }

        // Update password hash and reset any temporary lockout / mustChangePassword states
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        user.setFailedAttemptCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        // Mark the current token as used
        Instant now = Instant.now();
        token.setUsedAt(now);
        passwordResetTokenRepository.save(token);

        // Invalidate any other outstanding active tokens for this user
        passwordResetTokenRepository.invalidateAllActiveTokensForUser(user, now);

        log.info("Password reset completed successfully for user id: {}", user.getId());

        return MessageResponse.builder()
                .message(RESET_PASSWORD_SUCCESS_MESSAGE)
                .build();
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


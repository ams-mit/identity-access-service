package lk.ac.kelaniya.ams.identity_access_service.service;

import lk.ac.kelaniya.ams.identity_access_service.dto.request.LoginRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.request.RegisterRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.LoginResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.RegisterResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lk.ac.kelaniya.ams.identity_access_service.entity.User;
import lk.ac.kelaniya.ams.identity_access_service.exception.AccountLockedException;
import lk.ac.kelaniya.ams.identity_access_service.exception.AccountStatusException;
import lk.ac.kelaniya.ams.identity_access_service.exception.DuplicateEmailException;
import lk.ac.kelaniya.ams.identity_access_service.exception.InvalidCredentialsException;
import lk.ac.kelaniya.ams.identity_access_service.exception.PasswordMismatchException;
import lk.ac.kelaniya.ams.identity_access_service.repository.UserRepository;
import lk.ac.kelaniya.ams.identity_access_service.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Service managing user registration and authentication lifecycle.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

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
}

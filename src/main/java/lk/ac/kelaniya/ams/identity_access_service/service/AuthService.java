package lk.ac.kelaniya.ams.identity_access_service.service;

import lk.ac.kelaniya.ams.identity_access_service.dto.request.LoginRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.request.RegisterRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.LoginResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.RegisterResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lk.ac.kelaniya.ams.identity_access_service.entity.User;
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

import java.util.List;
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
     * Registers a new user account with PENDING_VERIFICATION status.
     * Enforces password match, uniqueness of email, and BCrypt hashing.
     *
     * @param request the registration request payload
     * @return the created user details
     * @throws PasswordMismatchException if password and confirmPassword do not match
     * @throws DuplicateEmailException   if email is already in use
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new PasswordMismatchException("Passwords do not match");
        }

        String email = request.getEmail().trim();
        if (userRepository.existsByEmail(email)) {
            log.warn("Registration rejected: duplicate email address attempt");
            throw new DuplicateEmailException("Email already in use");
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());

        User user = User.builder()
                .email(email)
                .username(email)
                .passwordHash(passwordHash)
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .phone(request.getPhone() != null ? request.getPhone().trim() : null)
                .accountStatus(AccountStatus.PENDING_VERIFICATION)
                .failedAttemptCount(0)
                .build();

        User savedUser = userRepository.save(user);

        log.info("User registered successfully with id: {}", savedUser.getId());

        return RegisterResponse.builder()
                .userId(savedUser.getId())
                .email(savedUser.getEmail())
                .accountStatus(savedUser.getAccountStatus())
                .build();
    }

    /**
     * Authenticates user credentials, verifies account lifecycle status, and issues an RS256-signed JWT.
     * Enforces anti-enumeration: user-not-found and wrong password both produce identical generic 401 errors.
     * Password validation strictly precedes account status checks to prevent disclosing account existence.
     *
     * @param request login credentials payload
     * @return authentication response containing RS256 JWT, expiry in seconds, and user details
     * @throws InvalidCredentialsException if email is not found or password does not match (generic 401)
     * @throws AccountStatusException      if account is PENDING_VERIFICATION, SUSPENDED, or DEACTIVATED (403)
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = request.getEmail().trim();
        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isEmpty()) {
            log.warn("Authentication failed: user not found for email: {}", email);
            throw new InvalidCredentialsException("Invalid email or password.");
        }

        User user = userOptional.get();

        // 1. Verify password FIRST before checking account status to prevent email enumeration
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Authentication failed: invalid password for user id: {}", user.getId());
            throw new InvalidCredentialsException("Invalid email or password.");
        }

        // 2. Check account status AFTER confirming credentials are valid
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
        } else if (status != AccountStatus.ACTIVE) {
            log.warn("Authentication rejected: non-active account status {} for user id: {}", status, user.getId());
            throw new AccountStatusException("ACCOUNT_INACTIVE", "Account is not active.");
        }

        // 3. Extract roles (empty list if no roles assigned yet)
        List<String> roles = (user.getUserRoles() != null && !user.getUserRoles().isEmpty())
                ? user.getUserRoles().stream()
                    .map(ur -> ur.getRole() != null ? ur.getRole().getName() : null)
                    .filter(Objects::nonNull)
                    .toList()
                : List.of();

        // 4. Issue RS256 JWT
        String token = jwtService.generateToken(user.getId(), user.getEmail(), roles);
        long expiresIn = jwtService.getExpirationSeconds();

        log.info("User authenticated successfully with id: {}", user.getId());

        return LoginResponse.builder()
                .accessToken(token)
                .expiresIn(expiresIn)
                .user(LoginResponse.UserSummary.builder()
                        .userId(user.getId())
                        .email(user.getEmail())
                        .roles(roles)
                        .build())
                .build();
    }
}

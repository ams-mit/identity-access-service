package lk.ac.kelaniya.ams.identity_access_service.service;

import lk.ac.kelaniya.ams.identity_access_service.dto.request.ChangePasswordRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.UserSummaryResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lk.ac.kelaniya.ams.identity_access_service.entity.User;
import lk.ac.kelaniya.ams.identity_access_service.exception.AccountStatusException;
import lk.ac.kelaniya.ams.identity_access_service.exception.InvalidCredentialsException;
import lk.ac.kelaniya.ams.identity_access_service.exception.PasswordMismatchException;
import lk.ac.kelaniya.ams.identity_access_service.exception.SamePasswordException;
import lk.ac.kelaniya.ams.identity_access_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lk.ac.kelaniya.ams.identity_access_service.entity.AuditEventType;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Service managing user profile retrieval and account operations.
 */
@Slf4j
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this(userRepository, passwordEncoder, null);
    }

    public UserService(UserRepository userRepository) {
        this(userRepository, null, null);
    }

    /**
     * Retrieve the current authenticated user's profile directly from the database.
     *
     * @param userId unique identifier of the user
     * @return UserSummaryResponse containing fresh user details
     */
    @Transactional(readOnly = true)
    public UserSummaryResponse getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("User not found or no longer exists"));

        if (user.getAccountStatus() == AccountStatus.DEACTIVATED) {
            log.warn("Access denied for deactivated user: {}", userId);
            throw new AccountStatusException("ACCOUNT_DEACTIVATED", "Account has been deactivated. Please contact support.");
        }

        List<String> roles = (user.getUserRoles() != null && !user.getUserRoles().isEmpty())
                ? user.getUserRoles().stream()
                    .map(ur -> ur.getRole() != null ? ur.getRole().getName() : null)
                    .filter(Objects::nonNull)
                    .toList()
                : List.of();

        List<String> permissions = (user.getUserRoles() != null && !user.getUserRoles().isEmpty())
                ? user.getUserRoles().stream()
                    .map(lk.ac.kelaniya.ams.identity_access_service.entity.UserRole::getRole)
                    .filter(Objects::nonNull)
                    .filter(r -> r.getPermissions() != null)
                    .flatMap(r -> r.getPermissions().stream())
                    .map(lk.ac.kelaniya.ams.identity_access_service.entity.Permission::getCode)
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted()
                    .toList()
                : List.of();

        return UserSummaryResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .accountStatus(user.getAccountStatus())
                .roles(roles)
                .permissions(permissions)
                .requestedRole(user.getRequestedRole())
                .build();
    }

    /**
     * Change password for authenticated user.
     * Verifies current password via BCrypt, ensures new password matches confirmation
     * and differs from current password, persists updated BCrypt hash, and clears mustChangePassword flag.
     * Does not touch failedAttemptCount, lockedUntil, or role/status fields.
     *
     * @param userId  unique identifier of the user
     * @param request change password payload containing current and new passwords
     * @throws InvalidCredentialsException if user not found or current password does not match
     * @throws PasswordMismatchException    if newPassword does not match confirmNewPassword
     * @throws SamePasswordException        if newPassword equals currentPassword or matches existing hash
     */
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("Current password is incorrect."));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            log.warn("Password change failed: invalid current password for user id: {}", userId);
            throw new InvalidCredentialsException("Current password is incorrect.");
        }

        if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
            log.warn("Password change failed: mismatched new and confirm passwords for user id: {}", userId);
            throw new PasswordMismatchException("New password and confirm password do not match");
        }

        if (request.getNewPassword().equals(request.getCurrentPassword()) ||
                passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            log.warn("Password change failed: new password is same as current password for user id: {}", userId);
            throw new SamePasswordException("New password must differ from current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);

        log.info("Password changed successfully and mustChangePassword cleared for user id: {}", userId);

        if (auditService != null) {
            auditService.record(
                    AuditEventType.PASSWORD_CHANGED,
                    userId,
                    userId,
                    null,
                    null,
                    "User self-service password change"
            );
        }
    }
}

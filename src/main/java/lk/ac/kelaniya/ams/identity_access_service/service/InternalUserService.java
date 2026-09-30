package lk.ac.kelaniya.ams.identity_access_service.service;

import lk.ac.kelaniya.ams.identity_access_service.dto.response.InternalUserResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.UpdateUserEmailResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.AuditEventType;
import lk.ac.kelaniya.ams.identity_access_service.entity.User;
import lk.ac.kelaniya.ams.identity_access_service.exception.DuplicateEmailException;
import lk.ac.kelaniya.ams.identity_access_service.exception.UserNotFoundException;
import lk.ac.kelaniya.ams.identity_access_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Service providing user identity and authorization details exclusively for internal microservice consumption.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InternalUserService {

    private final UserRepository userRepository;
    private final AuditService auditService;

    /**
     * Retrieves minimal authorization details (userId, accountStatus, roles) for a specific user.
     * Excludes all PII and sensitive fields.
     *
     * @param userId unique user identifier
     * @return minimal internal user response
     * @throws UserNotFoundException if user does not exist
     */
    @Transactional(readOnly = true)
    public InternalUserResponse getUserForValidation(UUID userId) {
        log.info("Internal user validation requested for user id: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("Internal user validation failed: user not found with id: {}", userId);
                    return new UserNotFoundException("User not found with id: " + userId);
                });

        List<String> roles = user.getUserRoles() != null
                ? user.getUserRoles().stream()
                        .map(ur -> ur.getRole() != null ? ur.getRole().getName() : null)
                        .filter(Objects::nonNull)
                        .sorted()
                        .toList()
                : List.of();

        return InternalUserResponse.builder()
                .userId(user.getId())
                .accountStatus(user.getAccountStatus())
                .roles(roles)
                .build();
    }

    /**
     * Updates a user's email address on behalf of an authorized internal service (e.g. resident-management-service).
     *
     * @param userId        target user identifier
     * @param newEmail      new email address
     * @param callerService name of calling service performing the update
     * @return updated user email response
     * @throws UserNotFoundException   if user does not exist
     * @throws DuplicateEmailException if email is already in use by another user
     */
    @Transactional
    public UpdateUserEmailResponse updateUserEmail(UUID userId, String newEmail, String callerService) {
        log.info("Internal email update requested for user id: {} by caller: {}", userId, callerService);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("Internal email update failed: user not found with id: {}", userId);
                    return new UserNotFoundException("User not found with id: " + userId);
                });

        String normalizedEmail = newEmail.trim().toLowerCase(Locale.ROOT);

        userRepository.findByEmail(normalizedEmail).ifPresent(existingUser -> {
            if (!existingUser.getId().equals(userId)) {
                log.warn("Internal email update conflict: email '{}' already in use by user id: {}", normalizedEmail, existingUser.getId());
                throw new DuplicateEmailException("Email is already registered: " + normalizedEmail);
            }
        });

        String oldEmail = user.getEmail();
        user.setEmail(normalizedEmail);
        user.setUsername(normalizedEmail);
        userRepository.save(user);

        if (auditService != null) {
            auditService.record(
                    AuditEventType.EMAIL_CHANGED,
                    userId,
                    null,
                    oldEmail,
                    normalizedEmail,
                    "Updated by caller service: " + callerService
            );
        }

        log.info("User id: {} email successfully updated from '{}' to '{}'", userId, oldEmail, normalizedEmail);

        return UpdateUserEmailResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .build();
    }
}

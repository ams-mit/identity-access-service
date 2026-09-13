package lk.ac.kelaniya.ams.identity_access_service.service;

import lk.ac.kelaniya.ams.identity_access_service.dto.request.UpdateAccountStatusRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.AdminUserDetailResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.AdminUserSummaryResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lk.ac.kelaniya.ams.identity_access_service.entity.User;
import lk.ac.kelaniya.ams.identity_access_service.exception.InvalidStatusTransitionException;
import lk.ac.kelaniya.ams.identity_access_service.exception.UserNotFoundException;
import lk.ac.kelaniya.ams.identity_access_service.repository.UserRepository;
import lk.ac.kelaniya.ams.identity_access_service.repository.specification.UserSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Service managing administrative user inspection, search, and detail retrieval.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;

    /**
     * Search and paginate users with optional filtering on free-text query, account status,
     * and requested role.
     *
     * @param query         free-text match against name or email
     * @param status        account status filter
     * @param requestedRole advisory requested role filter
     * @param pageable      Spring Data pagination and sorting information
     * @return paginated list of user summary DTOs
     */
    @Transactional(readOnly = true)
    public Page<AdminUserSummaryResponse> searchUsers(
            String query,
            AccountStatus status,
            String requestedRole,
            Pageable pageable
    ) {
        log.debug("Searching users: query='{}', status={}, requestedRole={}, pageable={}",
                query, status, requestedRole, pageable);

        Specification<User> spec = UserSpecifications.withFilters(query, status, requestedRole);
        Page<User> usersPage = userRepository.findAll(spec, pageable);

        return usersPage.map(this::toSummaryResponse);
    }

    /**
     * Retrieve complete user profile details for administrative inspection.
     *
     * @param userId unique user identifier
     * @return full user detail DTO including requested role and profile fields
     * @throws UserNotFoundException if no user exists with the given ID
     */
    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUserById(UUID userId) {
        log.debug("Retrieving user details for admin inspection: userId={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));

        return toDetailResponse(user);
    }

    private static final Map<AccountStatus, Set<AccountStatus>> ALLOWED_TRANSITIONS = Map.of(
            AccountStatus.PENDING_VERIFICATION, Set.of(AccountStatus.ACTIVE, AccountStatus.REJECTED),
            AccountStatus.ACTIVE, Set.of(AccountStatus.SUSPENDED, AccountStatus.DEACTIVATED),
            AccountStatus.SUSPENDED, Set.of(AccountStatus.ACTIVE, AccountStatus.DEACTIVATED)
    );

    /**
     * Transition a user's account lifecycle status according to the finite state machine.
     * Valid transitions:
     * - PENDING_VERIFICATION -> ACTIVE (activate/approve)
     * - PENDING_VERIFICATION -> REJECTED (reason required)
     * - ACTIVE -> SUSPENDED (reason required)
     * - SUSPENDED -> ACTIVE (reactivate)
     * - ACTIVE -> DEACTIVATED (reason required)
     * - SUSPENDED -> DEACTIVATED (reason required)
     *
     * @param userId  unique identifier of target user
     * @param request status update payload containing target status and optional/required reason
     * @param adminId authenticated administrator performing the transition
     * @return updated user details
     * @throws UserNotFoundException            if target user does not exist
     * @throws InvalidStatusTransitionException if transition is disallowed or required reason is missing
     */
    @Transactional
    public AdminUserDetailResponse updateAccountStatus(
            UUID userId,
            UpdateAccountStatusRequest request,
            UUID adminId
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));

        AccountStatus currentStatus = user.getAccountStatus();
        AccountStatus targetStatus = request.getStatus();

        Set<AccountStatus> allowedTargets = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (targetStatus == null || !allowedTargets.contains(targetStatus)) {
            log.warn("Invalid account status transition attempt for user id {}: {} -> {}", userId, currentStatus, targetStatus);
            throw new InvalidStatusTransitionException(
                    String.format(
                            "Invalid account status transition from %s to %s. Allowed transitions: "
                                    + "PENDING_VERIFICATION -> [ACTIVE, REJECTED], "
                                    + "ACTIVE -> [SUSPENDED, DEACTIVATED], "
                                    + "SUSPENDED -> [ACTIVE, DEACTIVATED].",
                            currentStatus, targetStatus
                    )
            );
        }

        String reason = request.getReason() != null ? request.getReason().trim() : "";
        boolean reasonRequired = (targetStatus == AccountStatus.SUSPENDED
                || targetStatus == AccountStatus.DEACTIVATED
                || targetStatus == AccountStatus.REJECTED);

        if (reasonRequired && reason.isEmpty()) {
            log.warn("Status transition to {} rejected for user id {}: missing required reason", targetStatus, userId);
            throw new InvalidStatusTransitionException(
                    "Reason is required when transitioning account status to " + targetStatus + "."
            );
        }

        log.info("AUDIT: Account status transition for user id: {} from {} to {} by admin id: {}. Reason: '{}'",
                user.getId(), currentStatus, targetStatus, adminId, reason);

        user.setAccountStatus(targetStatus);
        User savedUser = userRepository.save(user);

        return toDetailResponse(savedUser);
    }

    private AdminUserSummaryResponse toSummaryResponse(User user) {
        return AdminUserSummaryResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(buildFullName(user.getFirstName(), user.getLastName()))
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .accountStatus(user.getAccountStatus())
                .requestedRole(user.getRequestedRole())
                .roles(extractRoles(user))
                .build();
    }

    private AdminUserDetailResponse toDetailResponse(User user) {
        return AdminUserDetailResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(buildFullName(user.getFirstName(), user.getLastName()))
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .accountStatus(user.getAccountStatus())
                .requestedRole(user.getRequestedRole())
                .roles(extractRoles(user))
                .failedAttemptCount(user.getFailedAttemptCount())
                .lockedUntil(user.getLockedUntil())
                .accountLocked(user.isAccountLocked())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private String buildFullName(String firstName, String lastName) {
        if (firstName == null && lastName == null) {
            return null;
        }
        return ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();
    }

    private List<String> extractRoles(User user) {
        if (user.getUserRoles() == null || user.getUserRoles().isEmpty()) {
            return List.of();
        }
        return user.getUserRoles().stream()
                .map(ur -> ur.getRole() != null ? ur.getRole().getName() : null)
                .filter(Objects::nonNull)
                .toList();
    }
}

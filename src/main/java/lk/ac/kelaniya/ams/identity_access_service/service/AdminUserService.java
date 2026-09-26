package lk.ac.kelaniya.ams.identity_access_service.service;

import lk.ac.kelaniya.ams.identity_access_service.dto.request.AdminCreateUserRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.request.UpdateAccountStatusRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.AdminCreateUserResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.AdminUserDetailResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.AdminUserSummaryResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lk.ac.kelaniya.ams.identity_access_service.entity.Role;
import lk.ac.kelaniya.ams.identity_access_service.entity.User;
import lk.ac.kelaniya.ams.identity_access_service.exception.DuplicateEmailException;
import lk.ac.kelaniya.ams.identity_access_service.exception.InvalidRoleException;
import lk.ac.kelaniya.ams.identity_access_service.exception.InvalidStatusTransitionException;
import lk.ac.kelaniya.ams.identity_access_service.exception.RoleAlreadyAssignedException;
import lk.ac.kelaniya.ams.identity_access_service.exception.RoleNotAssignedException;
import lk.ac.kelaniya.ams.identity_access_service.exception.SelfRoleAssignmentException;
import lk.ac.kelaniya.ams.identity_access_service.exception.UserNotFoundException;
import lk.ac.kelaniya.ams.identity_access_service.repository.RoleRepository;
import lk.ac.kelaniya.ams.identity_access_service.repository.UserRepository;
import lk.ac.kelaniya.ams.identity_access_service.repository.specification.UserSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lk.ac.kelaniya.ams.identity_access_service.entity.AuditEventType;
import java.util.UUID;

/**
 * Service managing administrative user inspection, search, and detail retrieval.
 */
@Slf4j
@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final EmailService emailService;

    @Autowired
    public AdminUserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            EmailService emailService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.emailService = emailService;
    }

    public AdminUserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService
    ) {
        this(userRepository, roleRepository, passwordEncoder, auditService, null);
    }

    public AdminUserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder
    ) {
        this(userRepository, roleRepository, passwordEncoder, null, null);
    }

    private void recordAudit(
            AuditEventType type,
            UUID subjectUserId,
            UUID actorUserId,
            String oldValue,
            String newValue,
            String reason
    ) {
        if (auditService != null) {
            auditService.record(type, subjectUserId, actorUserId, oldValue, newValue, reason);
        }
    }

    public static final Set<String> VALID_ROLES = Set.of(
            "SYSTEM_ADMINISTRATOR",
            "APARTMENT_MANAGER",
            "OWNER",
            "TENANT_RESIDENT",
            "FINANCE_OFFICER",
            "MAINTENANCE_COORDINATOR",
            "TECHNICIAN",
            "SECURITY_OFFICER"
    );

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

        recordAudit(
                AuditEventType.ACCOUNT_STATUS_CHANGED,
                savedUser.getId(),
                adminId,
                currentStatus.name(),
                targetStatus.name(),
                reason.isEmpty() ? null : reason
        );

        if (emailService != null && currentStatus == AccountStatus.PENDING_VERIFICATION) {
            try {
                if (targetStatus == AccountStatus.ACTIVE) {
                    emailService.sendRegistrationOutcomeEmail(savedUser.getEmail(), savedUser.getFirstName(), true, null);
                } else if (targetStatus == AccountStatus.REJECTED) {
                    emailService.sendRegistrationOutcomeEmail(savedUser.getEmail(), savedUser.getFirstName(), false, reason);
                }
            } catch (Exception e) {
                log.error("Failed to dispatch registration outcome email for user id {}: {}", savedUser.getId(), e.getMessage(), e);
            }
        }

        return toDetailResponse(savedUser);
    }

    /**
     * Assigns a staff or system role to a user.
     * A user may hold multiple roles simultaneously.
     * The assigned role is not constrained to match requestedRole.
     * Self-assignment by an administrator is strictly prohibited.
     * Account status is not touched by this operation.
     *
     * @param userId   unique user identifier
     * @param roleName role to assign (must be one of the 8 valid AMS roles)
     * @param adminId  identifier of authenticated administrator performing the assignment
     * @return updated user details
     * @throws InvalidRoleException         if roleName is not one of the 8 valid AMS roles or role entity is missing
     * @throws SelfRoleAssignmentException  if administrator attempts to assign a role to their own account
     * @throws UserNotFoundException        if target user does not exist
     * @throws RoleAlreadyAssignedException if user already holds the specified role
     */
    @Transactional
    public AdminUserDetailResponse assignRole(UUID userId, String roleName, UUID adminId) {
        if (roleName == null || !VALID_ROLES.contains(roleName.trim())) {
            log.warn("Role assignment rejected for user id {}: invalid role name '{}'", userId, roleName);
            throw new InvalidRoleException(
                    "Invalid role: '" + roleName + "'. Valid roles are: " + String.join(", ", VALID_ROLES)
            );
        }
        String normalizedRole = roleName.trim();

        if (adminId != null && adminId.equals(userId)) {
            log.warn("Self-assignment attempt blocked: admin id {} attempted to assign role '{}' to own account", adminId, normalizedRole);
            throw new SelfRoleAssignmentException("Administrators cannot assign roles to their own account.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));

        boolean alreadyHasRole = user.getUserRoles() != null && user.getUserRoles().stream()
                .anyMatch(ur -> ur.getRole() != null && normalizedRole.equalsIgnoreCase(ur.getRole().getName()));
        if (alreadyHasRole) {
            log.warn("Role assignment conflict for user id {}: user already holds role '{}'", userId, normalizedRole);
            throw new RoleAlreadyAssignedException("User already holds the role: " + normalizedRole);
        }

        Role role = roleRepository.findByName(normalizedRole)
                .orElseThrow(() -> new InvalidRoleException("Role not found: " + normalizedRole));

        user.addRole(role);
        log.info("AUDIT: Role '{}' assigned to user id: {} by admin id: {}", normalizedRole, userId, adminId);
        User savedUser = userRepository.save(user);

        recordAudit(
                AuditEventType.ROLE_ASSIGNED,
                savedUser.getId(),
                adminId,
                null,
                normalizedRole,
                null
        );

        return toDetailResponse(savedUser);
    }

    /**
     * Removes a specific granted role from a user.
     * Self-removal by an administrator is strictly prohibited.
     * Account status is not touched by this operation.
     *
     * @param userId   unique user identifier
     * @param roleName role to remove (must be one of the 8 valid AMS roles)
     * @param adminId  identifier of authenticated administrator performing the removal
     * @return updated user details
     * @throws InvalidRoleException        if roleName is not one of the 8 valid AMS roles or role entity is missing
     * @throws SelfRoleAssignmentException if administrator attempts to remove a role from their own account
     * @throws UserNotFoundException       if target user does not exist
     * @throws RoleNotAssignedException    if user does not hold the specified role
     */
    @Transactional
    public AdminUserDetailResponse removeRole(UUID userId, String roleName, UUID adminId) {
        if (roleName == null || !VALID_ROLES.contains(roleName.trim())) {
            log.warn("Role removal rejected for user id {}: invalid role name '{}'", userId, roleName);
            throw new InvalidRoleException(
                    "Invalid role: '" + roleName + "'. Valid roles are: " + String.join(", ", VALID_ROLES)
            );
        }
        String normalizedRole = roleName.trim();

        if (adminId != null && adminId.equals(userId)) {
            log.warn("Self-removal attempt blocked: admin id {} attempted to remove role '{}' from own account", adminId, normalizedRole);
            throw new SelfRoleAssignmentException("Administrators cannot remove roles from their own account.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));

        boolean hasRole = user.getUserRoles() != null && user.getUserRoles().stream()
                .anyMatch(ur -> ur.getRole() != null && normalizedRole.equalsIgnoreCase(ur.getRole().getName()));
        if (!hasRole) {
            log.warn("Role removal conflict for user id {}: user does not hold role '{}'", userId, normalizedRole);
            throw new RoleNotAssignedException("User does not hold the role: " + normalizedRole);
        }

        Role role = roleRepository.findByName(normalizedRole)
                .orElseThrow(() -> new InvalidRoleException("Role not found: " + normalizedRole));

        user.removeRole(role);
        log.info("AUDIT: Role '{}' removed from user id: {} by admin id: {}", normalizedRole, userId, adminId);
        User savedUser = userRepository.save(user);

        recordAudit(
                AuditEventType.ROLE_REMOVED,
                savedUser.getId(),
                adminId,
                normalizedRole,
                null,
                null
        );

        return toDetailResponse(savedUser);
    }

    /**
     * Administratively creates a new user account with ACTIVE status and a temporary password.
     * The user is immediately flagged to change their password on first login (mustChangePassword = true).
     * The account is created with zero granted roles (role assignment is performed separately via IAM-07).
     *
     * @param request account creation payload containing personal details and temporary password
     * @return creation response containing userId, email, accountStatus, and mustChangePassword
     * @throws DuplicateEmailException if the email address is already registered
     */
    @Transactional
    public AdminCreateUserResponse createUser(AdminCreateUserRequest request) {
        return createUser(request, null);
    }

    /**
     * Administratively creates a new user account with ACTIVE status, temporary password, and records auditing.
     *
     * @param request account creation payload
     * @param adminId authenticated administrator performing creation
     * @return creation response
     */
    @Transactional
    public AdminCreateUserResponse createUser(AdminCreateUserRequest request, UUID adminId) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            log.warn("Admin user creation rejected: duplicate email address '{}'", email);
            throw new DuplicateEmailException("Email already in use");
        }

        List<Role> rolesToAssign = new ArrayList<>();
        if (request.getInitialRoles() != null && !request.getInitialRoles().isEmpty()) {
            Set<String> seenRoles = new HashSet<>();
            for (String roleName : request.getInitialRoles()) {
                if (roleName == null || !VALID_ROLES.contains(roleName.trim())) {
                    log.warn("Admin user creation rejected: invalid role name '{}'", roleName);
                    throw new InvalidRoleException(
                            "Invalid role: '" + roleName + "'. Valid roles are: " + String.join(", ", VALID_ROLES)
                    );
                }
                String normalizedRole = roleName.trim();
                if (seenRoles.add(normalizedRole)) {
                    Role role = roleRepository.findByName(normalizedRole)
                            .orElseThrow(() -> new InvalidRoleException("Role not found: " + normalizedRole));
                    rolesToAssign.add(role);
                }
            }
        }

        String passwordHash = passwordEncoder.encode(request.getTemporaryPassword());

        User user = User.builder()
                .email(email)
                .username(email)
                .passwordHash(passwordHash)
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .phone(request.getPhone() != null ? request.getPhone().trim() : null)
                // requestedRole is specific to the self-registration review workflow;
                // admin-created accounts are provisioned directly without an advisory role application.
                .requestedRole(null)
                // Admin creation is implicit administrative approval; initial status is ACTIVE immediately.
                .accountStatus(AccountStatus.ACTIVE)
                // Admin sets a temporary password; the user must change it upon initial login.
                .mustChangePassword(true)
                .failedAttemptCount(0)
                .build();

        for (Role role : rolesToAssign) {
            user.addRole(role);
        }

        User savedUser = userRepository.save(user);
        log.info("AUDIT: Admin-created user account successfully saved with id: {} and email: {}",
                savedUser.getId(), savedUser.getEmail());

        recordAudit(
                AuditEventType.USER_CREATED_BY_ADMIN,
                savedUser.getId(),
                adminId,
                null,
                savedUser.getEmail(),
                null
        );

        for (Role role : rolesToAssign) {
            log.info("AUDIT: Initial role '{}' assigned to user id: {} by admin id: {}", role.getName(), savedUser.getId(), adminId);
            recordAudit(
                    AuditEventType.ROLE_ASSIGNED,
                    savedUser.getId(),
                    adminId,
                    null,
                    role.getName(),
                    null
            );
        }

        List<String> assignedRoleNames = rolesToAssign.stream()
                .map(Role::getName)
                .toList();

        return AdminCreateUserResponse.builder()
                .userId(savedUser.getId())
                .email(savedUser.getEmail())
                .accountStatus(savedUser.getAccountStatus())
                .mustChangePassword(savedUser.isMustChangePassword())
                .roles(assignedRoleNames)
                .build();
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
                .mustChangePassword(user.isMustChangePassword())
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

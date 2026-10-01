package kln.ams.identityaccess.service;

import kln.ams.identityaccess.dto.response.PagedData;
import kln.ams.identityaccess.dto.user.CreateUserRequest;
import kln.ams.identityaccess.dto.user.ReplaceUserRolesRequest;
import kln.ams.identityaccess.dto.user.UpdateStatusRequest;
import kln.ams.identityaccess.dto.user.UpdateUserRequest;
import kln.ams.identityaccess.dto.user.UserResponse;
import kln.ams.identityaccess.dto.user.UserRolesResponse;
import kln.ams.identityaccess.entity.AccountStatus;
import kln.ams.identityaccess.entity.Role;
import kln.ams.identityaccess.entity.User;
import kln.ams.identityaccess.exception.DuplicateResourceException;
import kln.ams.identityaccess.exception.InvalidRoleException;
import kln.ams.identityaccess.exception.UserNotFoundException;
import kln.ams.identityaccess.repository.RoleRepository;
import kln.ams.identityaccess.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    public static final Set<String> CANONICAL_ROLES = Set.of(
            "SYSTEM_ADMINISTRATOR",
            "APARTMENT_MANAGER",
            "OWNER",
            "TENANT_RESIDENT",
            "FINANCE_OFFICER",
            "MAINTENANCE_COORDINATOR",
            "TECHNICIAN",
            "SERVICE_STAFF",
            "SECURITY_OFFICER"
    );

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Page<UserResponse> getUsersPage(int page, int size, AccountStatus status, String role, String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> userPage = userRepository.findUsersFiltered(status, role, search, pageable);
        return userPage.map(this::toUserResponse);
    }

    @Transactional(readOnly = true)
    public PagedData<UserResponse> getUsers(int page, int size, AccountStatus status, String role, String search) {
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.min(Math.max(1, size), 100);

        Pageable pageable = PageRequest.of(boundedPage, boundedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> userPage = userRepository.findUsersFiltered(status, role, search, pageable);

        List<UserResponse> items = userPage.getContent().stream()
                .map(this::toUserResponse)
                .toList();

        return PagedData.<UserResponse>builder()
                .items(items)
                .page(userPage.getNumber())
                .size(userPage.getSize())
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .build();
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new DuplicateResourceException("USER_ALREADY_EXISTS", "Username already exists: " + username);
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("USER_ALREADY_EXISTS", "Email already exists: " + email);
        }

        List<Role> resolvedRoles = validateAndResolveRoles(request.getRoles());

        User user = User.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .phone(request.getPhone() != null ? request.getPhone().trim() : null)
                .accountStatus(AccountStatus.ACTIVE)
                .userRoles(new HashSet<>())
                .build();

        for (Role role : resolvedRoles) {
            user.addRole(role);
        }

        User savedUser = userRepository.save(user);
        log.info("Created user account '{}' (id: {}) with roles {}", savedUser.getUsername(), savedUser.getId(), request.getRoles());
        return toUserResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        return toUserResponse(user);
    }

    @Transactional
    public UserResponse updateUser(UUID userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            String newEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
            if (!newEmail.equalsIgnoreCase(user.getEmail())) {
                if (userRepository.existsByEmailIgnoreCaseAndIdNot(newEmail, userId)) {
                    throw new DuplicateResourceException("USERNAME_OR_EMAIL_ALREADY_EXISTS", "Email is already taken: " + newEmail);
                }
                user.setEmail(newEmail);
            }
        }

        if (request.getFirstName() != null && !request.getFirstName().isBlank()) {
            user.setFirstName(request.getFirstName().trim());
        }

        if (request.getLastName() != null && !request.getLastName().isBlank()) {
            user.setLastName(request.getLastName().trim());
        }

        if (request.getPhone() != null) {
            user.setPhone(request.getPhone().trim());
        }

        User savedUser = userRepository.save(user);
        log.info("Updated account fields for user id {}", userId);
        return toUserResponse(savedUser);
    }

    @Transactional
    public UserResponse updateStatus(UUID userId, UpdateStatusRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        AccountStatus targetStatus = request.getStatus();
        user.setAccountStatus(targetStatus);
        User savedUser = userRepository.save(user);
        log.info("Updated account status for user id {} to {} (reason: {})", userId, targetStatus, request.getReason());
        return toUserResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public UserRolesResponse getUserRoles(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        List<String> roles = user.getUserRoles() != null
                ? user.getUserRoles().stream()
                        .map(ur -> ur.getRole().getName())
                        .sorted()
                        .toList()
                : Collections.emptyList();

        return UserRolesResponse.builder()
                .userId(userId)
                .roles(roles)
                .build();
    }

    @Transactional
    public UserRolesResponse replaceUserRoles(UUID userId, ReplaceUserRolesRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        List<Role> resolvedRoles = validateAndResolveRoles(request.getRoles());

        user.clearRoles();
        for (Role role : resolvedRoles) {
            user.addRole(role);
        }

        userRepository.save(user);
        log.info("Replaced roles for user id {} with {}", userId, request.getRoles());

        List<String> updatedRoleNames = resolvedRoles.stream()
                .map(Role::getName)
                .sorted()
                .toList();

        return UserRolesResponse.builder()
                .userId(userId)
                .roles(updatedRoleNames)
                .build();
    }

    private List<Role> validateAndResolveRoles(List<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            throw new InvalidRoleException("At least one canonical role is required");
        }

        List<Role> roles = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String roleName : roleNames) {
            if (roleName == null || roleName.isBlank()) {
                throw new InvalidRoleException("Role name must not be blank");
            }
            String upper = roleName.trim().toUpperCase(Locale.ROOT);
            if (!CANONICAL_ROLES.contains(upper)) {
                throw new InvalidRoleException("Role '" + roleName + "' is not a recognized canonical role");
            }
            if (seen.add(upper)) {
                Role role = roleRepository.findByNameIgnoreCase(upper)
                        .orElseThrow(() -> new InvalidRoleException("Role '" + upper + "' does not exist in repository"));
                roles.add(role);
            }
        }

        return roles;
    }

    private UserResponse toUserResponse(User user) {
        List<String> roles = user.getUserRoles() != null
                ? user.getUserRoles().stream()
                        .map(ur -> ur.getRole().getName())
                        .sorted()
                        .toList()
                : Collections.emptyList();

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .status(user.getAccountStatus().name())
                .roles(roles)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}

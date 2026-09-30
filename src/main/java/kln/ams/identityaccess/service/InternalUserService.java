package kln.ams.identityaccess.service;

import kln.ams.identityaccess.dto.internal.UserStatusResponse;
import kln.ams.identityaccess.dto.internal.UserValidationResponse;
import kln.ams.identityaccess.entity.User;
import kln.ams.identityaccess.exception.UserNotFoundException;
import kln.ams.identityaccess.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InternalUserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserValidationResponse validateUser(UUID userId, String requiredRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        List<String> roles = user.getUserRoles() != null
                ? user.getUserRoles().stream()
                        .map(ur -> ur.getRole().getName())
                        .sorted()
                        .toList()
                : Collections.emptyList();

        boolean roleMatched = true;
        if (requiredRole != null && !requiredRole.isBlank()) {
            roleMatched = roles.stream().anyMatch(r -> r.equalsIgnoreCase(requiredRole.trim()));
        }

        boolean active = user.isActive() && roleMatched;

        return UserValidationResponse.builder()
                .userId(user.getId())
                .exists(true)
                .active(active)
                .roles(roles)
                .build();
    }

    @Transactional(readOnly = true)
    public UserStatusResponse getUserStatus(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        return UserStatusResponse.builder()
                .userId(user.getId())
                .status(user.getAccountStatus().name())
                .active(user.isActive())
                .build();
    }
}

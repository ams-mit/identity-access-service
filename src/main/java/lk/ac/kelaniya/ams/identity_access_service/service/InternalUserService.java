package lk.ac.kelaniya.ams.identity_access_service.service;

import lk.ac.kelaniya.ams.identity_access_service.dto.response.InternalUserResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.User;
import lk.ac.kelaniya.ams.identity_access_service.exception.UserNotFoundException;
import lk.ac.kelaniya.ams.identity_access_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
}

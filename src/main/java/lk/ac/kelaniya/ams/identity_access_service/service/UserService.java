package lk.ac.kelaniya.ams.identity_access_service.service;

import lk.ac.kelaniya.ams.identity_access_service.dto.response.UserSummaryResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lk.ac.kelaniya.ams.identity_access_service.entity.User;
import lk.ac.kelaniya.ams.identity_access_service.exception.AccountStatusException;
import lk.ac.kelaniya.ams.identity_access_service.exception.InvalidCredentialsException;
import lk.ac.kelaniya.ams.identity_access_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Service managing user profile retrieval and account operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

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

        return UserSummaryResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .accountStatus(user.getAccountStatus())
                .roles(roles)
                .build();
    }
}

package lk.ac.kelaniya.ams.identity_access_service.integration;

import lk.ac.kelaniya.ams.identity_access_service.dto.request.LoginRequest;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lk.ac.kelaniya.ams.identity_access_service.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test verifying full lockout flow (Flow 3).
 * Uses real MySQL container via Testcontainers and genuine HTTP calls without mocks.
 */
class AccountLockoutIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("Flow 3: 5 consecutive wrong password attempts -> 6th attempt (even with correct password) returns HTTP 423 -> verify real locked_until in database")
    void testFailedLoginLockoutPolicyEndToEnd() {
        String email = "lockout.target@ams.lk";
        String correctPassword = "CorrectSecretPassword123";
        String wrongPassword = "WrongPassword123";

        // Seed an active user directly in the database
        User user = seedUserWithRole(email, correctPassword, "TENANT_RESIDENT", AccountStatus.ACTIVE);
        assertThat(user.getId()).isNotNull();

        // Step 1: Attempt login with wrong password 5 times via real HTTP calls
        for (int i = 1; i <= 5; i++) {
            LoginRequest badLoginRequest = LoginRequest.builder()
                    .email(email)
                    .password(wrongPassword)
                    .build();

            ResponseEntity<String> badResponse = restTemplate.postForEntity(
                    "/api/v1/auth/login",
                    badLoginRequest,
                    String.class
            );

            // Each bad attempt returns 401 Unauthorized
            assertThat(badResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

            // Check intermediate failed attempt counter in real DB
            Optional<User> intermediateUser = userRepository.findById(user.getId());
            assertThat(intermediateUser).isPresent();
            assertThat(intermediateUser.get().getFailedAttemptCount()).isEqualTo(i);
            if (i < 5) {
                assertThat(intermediateUser.get().getLockedUntil()).isNull();
            } else {
                // On the 5th failed attempt, account is immediately locked for 15 minutes
                assertThat(intermediateUser.get().getLockedUntil()).isNotNull();
                assertThat(intermediateUser.get().getLockedUntil()).isAfter(Instant.now());
            }
        }

        // Step 2: Confirm the 6th attempt (even with correct password) returns real 423 Locked
        LoginRequest correctLoginRequest = LoginRequest.builder()
                .email(email)
                .password(correctPassword)
                .build();

        ResponseEntity<String> lockedResponse = restTemplate.postForEntity(
                "/api/v1/auth/login",
                correctLoginRequest,
                String.class
        );

        assertThat(lockedResponse.getStatusCode()).isEqualTo(HttpStatus.LOCKED); // HTTP 423
        assertThat(lockedResponse.getBody()).contains("Account is temporarily locked");

        // Step 3: Confirm this reflects real locked_until data in the real database
        Optional<User> lockedDbUser = userRepository.findById(user.getId());
        assertThat(lockedDbUser).isPresent();
        assertThat(lockedDbUser.get().getFailedAttemptCount()).isEqualTo(5);
        assertThat(lockedDbUser.get().getLockedUntil()).isNotNull();
        assertThat(lockedDbUser.get().getLockedUntil()).isAfter(Instant.now());
        assertThat(lockedDbUser.get().isAccountLocked()).isTrue();
    }
}

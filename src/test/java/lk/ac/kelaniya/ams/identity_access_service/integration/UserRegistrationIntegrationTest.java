package lk.ac.kelaniya.ams.identity_access_service.integration;

import lk.ac.kelaniya.ams.identity_access_service.dto.request.RegisterRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.ApiResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.RegisterResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.UserSummaryResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lk.ac.kelaniya.ams.identity_access_service.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test verifying full registration -> login -> /users/me flow (Flow 1).
 * Uses real MySQL container via Testcontainers and genuine HTTP calls without mocks.
 */
class UserRegistrationIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("Flow 1: Real HTTP registration -> database verification -> real login -> /users/me profile verification")
    void testRegistrationLoginAndProfileRetrievalFlow() {
        String email = "resident.john@ams.lk";
        String password = "SecurePassword123";

        // Step 1: Register via real HTTP call
        RegisterRequest registerRequest = RegisterRequest.builder()
                .firstName("John")
                .lastName("Silva")
                .email(email)
                .phone("+94771234567")
                .password(password)
                .confirmPassword(password)
                .requestedRole("OWNER")
                .build();

        ResponseEntity<ApiResponse<RegisterResponse>> registerResponse = restTemplate.exchange(
                "/api/v1/auth/register",
                HttpMethod.POST,
                new HttpEntity<>(registerRequest),
                new ParameterizedTypeReference<ApiResponse<RegisterResponse>>() {}
        );

        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registerResponse.getBody()).isNotNull();
        RegisterResponse registered = registerResponse.getBody().getData();
        assertThat(registered).isNotNull();
        assertThat(registered.getUserId()).isNotNull();
        assertThat(registered.getEmail()).isEqualTo(email);
        assertThat(registered.getAccountStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(registered.getRequestedRole()).isEqualTo("OWNER");

        // Step 2: Confirm a real row exists in the real users table in the database
        Optional<User> userOptional = userRepository.findByEmail(email);
        assertThat(userOptional).isPresent();
        User realDbUser = userOptional.get();
        assertThat(realDbUser.getId()).isEqualTo(registered.getUserId());
        assertThat(realDbUser.getFirstName()).isEqualTo("John");
        assertThat(realDbUser.getLastName()).isEqualTo("Silva");
        assertThat(realDbUser.getPhone()).isEqualTo("+94771234567");
        assertThat(realDbUser.getRequestedRole()).isEqualTo("OWNER");
        assertThat(realDbUser.getAccountStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(passwordEncoder.matches(password, realDbUser.getPasswordHash())).isTrue();

        // Step 3: Activate the user so login is permitted (pending verification accounts reject login per SRS)
        realDbUser.setAccountStatus(AccountStatus.ACTIVE);
        userRepository.save(realDbUser);

        // Step 4: Login with those credentials over real HTTP call and extract the real JWT
        String accessToken = loginAndGetToken(email, password);
        assertThat(accessToken).isNotBlank();

        // Step 5: Use JWT as real Authorization header on GET /api/v1/users/me
        HttpEntity<Void> meRequestEntity = createAuthEntity(null, accessToken);
        ResponseEntity<ApiResponse<UserSummaryResponse>> meResponse = restTemplate.exchange(
                "/api/v1/users/me",
                HttpMethod.GET,
                meRequestEntity,
                new ParameterizedTypeReference<ApiResponse<UserSummaryResponse>>() {}
        );

        // Step 6: Confirm returned data matches what was registered
        assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(meResponse.getBody()).isNotNull();
        UserSummaryResponse profile = meResponse.getBody().getData();
        assertThat(profile).isNotNull();
        assertThat(profile.getUserId()).isEqualTo(realDbUser.getId());
        assertThat(profile.getEmail()).isEqualTo(email);
        assertThat(profile.getFirstName()).isEqualTo("John");
        assertThat(profile.getLastName()).isEqualTo("Silva");
        assertThat(profile.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(profile.getRequestedRole()).isEqualTo("OWNER");
    }
}

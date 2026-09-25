package lk.ac.kelaniya.ams.identity_access_service.integration;

import lk.ac.kelaniya.ams.identity_access_service.dto.request.AdminCreateUserRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.request.AssignRoleRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.request.ChangePasswordRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.request.LoginRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.AdminCreateUserResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.AdminUserDetailResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.LoginResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lk.ac.kelaniya.ams.identity_access_service.entity.Role;
import lk.ac.kelaniya.ams.identity_access_service.entity.User;
import lk.ac.kelaniya.ams.identity_access_service.entity.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test verifying full admin-created-account -> role-assignment -> login flow (Flow 2).
 * Uses real MySQL container via Testcontainers and genuine HTTP calls without mocks.
 */
class AdminAccountProvisioningIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("Flow 2: Admin creates user -> assigns role -> login with temp password (mustChangePassword=true) -> change password -> old password fails, new password succeeds")
    void testAdminAccountProvisioningRoleAssignmentAndMustChangePasswordLifecycle() {
        // Step 1: Pre-seed admin user directly in test DB with SYSTEM_ADMINISTRATOR role
        String adminEmail = "super.admin@ams.lk";
        String adminPassword = "AdminPassword123";
        User adminUser = seedAdminUser(adminEmail, adminPassword);
        assertThat(adminUser.getId()).isNotNull();

        // Obtain real admin JWT via real HTTP login
        String adminToken = loginAndGetToken(adminEmail, adminPassword);
        assertThat(adminToken).isNotBlank();

        // Step 2: Admin creates a user via POST /api/v1/users over real HTTP call
        String newUserEmail = "finance.officer@ams.lk";
        String tempPassword = "TempPassword123";
        AdminCreateUserRequest createRequest = AdminCreateUserRequest.builder()
                .firstName("Kamal")
                .lastName("Perera")
                .email(newUserEmail)
                .phone("+94711223344")
                .temporaryPassword(tempPassword)
                .build();

        HttpEntity<AdminCreateUserRequest> createEntity = createAuthEntity(createRequest, adminToken);
        ResponseEntity<AdminCreateUserResponse> createResponse = restTemplate.exchange(
                "/api/v1/users",
                HttpMethod.POST,
                createEntity,
                AdminCreateUserResponse.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AdminCreateUserResponse createdUserDto = createResponse.getBody();
        assertThat(createdUserDto).isNotNull();
        UUID newUserId = createdUserDto.getUserId();
        assertThat(newUserId).isNotNull();
        assertThat(createdUserDto.getEmail()).isEqualTo(newUserEmail);
        assertThat(createdUserDto.isMustChangePassword()).isTrue();
        assertThat(createdUserDto.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);

        // Verify real database state after account creation
        Optional<User> createdDbUserOpt = userRepository.findById(newUserId);
        assertThat(createdDbUserOpt).isPresent();
        User createdDbUser = createdDbUserOpt.get();
        assertThat(createdDbUser.getEmail()).isEqualTo(newUserEmail);
        assertThat(createdDbUser.isMustChangePassword()).isTrue();
        assertThat(createdDbUser.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(userRoleRepository.findByIdUserId(newUserId)).isEmpty();

        // Step 3: Admin assigns a role via POST /api/v1/users/{userId}/roles
        AssignRoleRequest roleRequest = AssignRoleRequest.builder()
                .role("FINANCE_OFFICER")
                .build();

        HttpEntity<AssignRoleRequest> assignRoleEntity = createAuthEntity(roleRequest, adminToken);
        ResponseEntity<AdminUserDetailResponse> assignRoleResponse = restTemplate.exchange(
                "/api/v1/users/" + newUserId + "/roles",
                HttpMethod.POST,
                assignRoleEntity,
                AdminUserDetailResponse.class
        );

        assertThat(assignRoleResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(assignRoleResponse.getBody()).isNotNull();
        assertThat(assignRoleResponse.getBody().getRoles()).contains("FINANCE_OFFICER");

        // Verify real database state after role assignment
        Optional<User> userAfterRoleOpt = userRepository.findById(newUserId);
        assertThat(userAfterRoleOpt).isPresent();
        List<UserRole> assignedRoles = userRoleRepository.findByIdUserId(newUserId);
        assertThat(assignedRoles).hasSize(1);
        UUID assignedRoleId = assignedRoles.get(0).getId().getRoleId();
        Optional<Role> assignedRoleOpt = roleRepository.findById(assignedRoleId);
        assertThat(assignedRoleOpt).isPresent();
        assertThat(assignedRoleOpt.get().getName()).isEqualTo("FINANCE_OFFICER");

        // Step 4: New user logs in with temporary password via real HTTP call
        LoginRequest initialLoginRequest = LoginRequest.builder()
                .email(newUserEmail)
                .password(tempPassword)
                .build();

        ResponseEntity<LoginResponse> initialLoginResponse = restTemplate.postForEntity(
                "/api/v1/auth/login",
                initialLoginRequest,
                LoginResponse.class
        );

        assertThat(initialLoginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        LoginResponse initialLoginBody = initialLoginResponse.getBody();
        assertThat(initialLoginBody).isNotNull();
        // Confirm mustChangePassword=true in the real response
        assertThat(initialLoginBody.isMustChangePassword()).isTrue();
        assertThat(initialLoginBody.getUser()).isNotNull();
        assertThat(initialLoginBody.getUser().getRoles()).contains("FINANCE_OFFICER");
        String newUserToken = initialLoginBody.getAccessToken();
        assertThat(newUserToken).isNotBlank();
        String incomingGatewayToken = reSignWithGatewayKey(newUserToken);

        // Step 5: User changes password via PUT /api/v1/users/me/password
        String newPermanentPassword = "NewPermanentSecret123";
        ChangePasswordRequest changePasswordRequest = ChangePasswordRequest.builder()
                .currentPassword(tempPassword)
                .newPassword(newPermanentPassword)
                .confirmNewPassword(newPermanentPassword)
                .build();

        HttpEntity<ChangePasswordRequest> changePasswordEntity = createAuthEntity(changePasswordRequest, incomingGatewayToken);
        ResponseEntity<Void> changePasswordResponse = restTemplate.exchange(
                "/api/v1/users/me/password",
                HttpMethod.PUT,
                changePasswordEntity,
                Void.class
        );

        assertThat(changePasswordResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify real database state: mustChangePassword is false and password hash updated
        Optional<User> userAfterPasswordChangeOpt = userRepository.findById(newUserId);
        assertThat(userAfterPasswordChangeOpt).isPresent();
        User userAfterPasswordChange = userAfterPasswordChangeOpt.get();
        assertThat(userAfterPasswordChange.isMustChangePassword()).isFalse();
        assertThat(passwordEncoder.matches(newPermanentPassword, userAfterPasswordChange.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(tempPassword, userAfterPasswordChange.getPasswordHash())).isFalse();

        // Step 6: Confirm old password now fails login with 401
        LoginRequest oldPasswordLoginRequest = LoginRequest.builder()
                .email(newUserEmail)
                .password(tempPassword)
                .build();

        ResponseEntity<String> oldLoginResponse = restTemplate.postForEntity(
                "/api/v1/auth/login",
                oldPasswordLoginRequest,
                String.class
        );

        assertThat(oldLoginResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Step 7: Confirm new password succeeds login with 200 and mustChangePassword=false
        LoginRequest newPasswordLoginRequest = LoginRequest.builder()
                .email(newUserEmail)
                .password(newPermanentPassword)
                .build();

        ResponseEntity<LoginResponse> newLoginResponse = restTemplate.postForEntity(
                "/api/v1/auth/login",
                newPasswordLoginRequest,
                LoginResponse.class
        );

        assertThat(newLoginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(newLoginResponse.getBody()).isNotNull();
        assertThat(newLoginResponse.getBody().isMustChangePassword()).isFalse();
        assertThat(newLoginResponse.getBody().getAccessToken()).isNotBlank();
    }
}

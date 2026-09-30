package lk.ac.kelaniya.ams.identity_access_service.integration;

import lk.ac.kelaniya.ams.identity_access_service.dto.request.UpdateAccountStatusRequest;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lk.ac.kelaniya.ams.identity_access_service.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test verifying role-authorization matrix across admin-only endpoints (Flow 4).
 * Uses real MySQL container via Testcontainers and genuine HTTP calls without mocks.
 */
class RoleAuthorizationMatrixIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("Flow 4: Role-authorization matrix across GET /api/v1/users and PATCH /api/v1/users/{userId}/status with SYSTEM_ADMINISTRATOR, TENANT_RESIDENT, and Unauthenticated")
    void testRoleAuthorizationMatrixAcrossEndpoints() {
        // 1. Seed users and generate real JWTs via real login
        String adminEmail = "matrix.admin@ams.lk";
        String adminPass = "AdminMatrixPass123";
        seedUserWithRole(adminEmail, adminPass, "SYSTEM_ADMINISTRATOR", AccountStatus.ACTIVE);
        String adminToken = loginAndGetToken(adminEmail, adminPass);

        String residentEmail = "matrix.resident@ams.lk";
        String residentPass = "ResidentMatrixPass123";
        seedUserWithRole(residentEmail, residentPass, "TENANT_RESIDENT", AccountStatus.ACTIVE);
        String residentToken = loginAndGetToken(residentEmail, residentPass);

        String financeEmail = "matrix.finance@ams.lk";
        String financePass = "FinanceMatrixPass123";
        seedUserWithRole(financeEmail, financePass, "FINANCE_OFFICER", AccountStatus.ACTIVE);
        String financeToken = loginAndGetToken(financeEmail, financePass);

        // Seed a target user whose status can be updated (PENDING_VERIFICATION -> ACTIVE)
        String targetEmail = "matrix.target@ams.lk";
        User targetUser = seedUserWithRole(targetEmail, "TargetPass123", "OWNER", AccountStatus.PENDING_VERIFICATION);
        UUID targetUserId = targetUser.getId();

        UpdateAccountStatusRequest statusUpdateRequest = UpdateAccountStatusRequest.builder()
                .status(AccountStatus.ACTIVE)
                .build();

        // -------------------------------------------------------------
        // MATRIX TEST 1: SYSTEM_ADMINISTRATOR (Full Admin Access -> 200)
        // -------------------------------------------------------------
        // GET /api/v1/users
        ResponseEntity<String> adminGetUsersResponse = restTemplate.exchange(
                "/api/v1/users",
                HttpMethod.GET,
                createAuthEntity(null, adminToken),
                String.class
        );
        assertThat(adminGetUsersResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // PATCH /api/v1/users/{userId}/status
        ResponseEntity<String> adminPatchStatusResponse = restTemplate.exchange(
                "/api/v1/users/" + targetUserId + "/status",
                HttpMethod.PATCH,
                createAuthEntity(statusUpdateRequest, adminToken),
                String.class
        );
        assertThat(adminPatchStatusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // -------------------------------------------------------------
        // MATRIX TEST 2: TENANT_RESIDENT (Non-Admin Role -> 403 FORBIDDEN)
        // -------------------------------------------------------------
        // GET /api/v1/users
        ResponseEntity<String> residentGetUsersResponse = restTemplate.exchange(
                "/api/v1/users",
                HttpMethod.GET,
                createAuthEntity(null, residentToken),
                String.class
        );
        assertThat(residentGetUsersResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // PATCH /api/v1/users/{userId}/status
        ResponseEntity<String> residentPatchStatusResponse = restTemplate.exchange(
                "/api/v1/users/" + targetUserId + "/status",
                HttpMethod.PATCH,
                createAuthEntity(statusUpdateRequest, residentToken),
                String.class
        );
        assertThat(residentPatchStatusResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // -------------------------------------------------------------
        // MATRIX TEST 3: FINANCE_OFFICER (Staff Non-Admin Role -> 403 FORBIDDEN)
        // -------------------------------------------------------------
        // GET /api/v1/users
        ResponseEntity<String> financeGetUsersResponse = restTemplate.exchange(
                "/api/v1/users",
                HttpMethod.GET,
                createAuthEntity(null, financeToken),
                String.class
        );
        assertThat(financeGetUsersResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // PATCH /api/v1/users/{userId}/status
        ResponseEntity<String> financePatchStatusResponse = restTemplate.exchange(
                "/api/v1/users/" + targetUserId + "/status",
                HttpMethod.PATCH,
                createAuthEntity(statusUpdateRequest, financeToken),
                String.class
        );
        assertThat(financePatchStatusResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // -------------------------------------------------------------
        // MATRIX TEST 4: UNAUTHENTICATED (No Token -> 401 UNAUTHORIZED)
        // -------------------------------------------------------------
        // GET /api/v1/users
        ResponseEntity<String> unauthenticatedGetUsersResponse = restTemplate.exchange(
                "/api/v1/users",
                HttpMethod.GET,
                createAuthEntity(null, null),
                String.class
        );
        assertThat(unauthenticatedGetUsersResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // PATCH /api/v1/users/{userId}/status
        ResponseEntity<String> unauthenticatedPatchStatusResponse = restTemplate.exchange(
                "/api/v1/users/" + targetUserId + "/status",
                HttpMethod.PATCH,
                createAuthEntity(statusUpdateRequest, null),
                String.class
        );
        assertThat(unauthenticatedPatchStatusResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}

package lk.ac.kelaniya.ams.identity_access_service.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lk.ac.kelaniya.ams.identity_access_service.dto.request.AssignRoleRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.request.UpdateAccountStatusRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.AdminUserDetailResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.AuditEventResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lk.ac.kelaniya.ams.identity_access_service.entity.AuditEvent;
import lk.ac.kelaniya.ams.identity_access_service.entity.AuditEventType;
import lk.ac.kelaniya.ams.identity_access_service.entity.User;
import lk.ac.kelaniya.ams.identity_access_service.repository.AuditEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test verifying audit event persistence in MySQL via Flyway V9
 * and querying via GET /api/v1/audit-logs (AMS1-S3-IAM-02 & AMS1-S3-IAM-03).
 */
class AuditTrailIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    @AfterEach
    void cleanAuditDatabase() {
        auditEventRepository.deleteAll();
    }

    @Test
    @DisplayName("End-to-end: Admin performs status change and role assignment -> audit records persisted in DB -> queried via GET /api/v1/audit-logs")
    void testAuditTrailEndToEnd_adminLifecycleActions_recordedAndQueriedSuccessfully() throws Exception {
        // Step 1: Seed admin and obtain Bearer token
        String adminEmail = "audit.admin@ams.lk";
        String adminPassword = "AdminPassword123";
        User adminUser = seedAdminUser(adminEmail, adminPassword);
        String adminToken = loginAndGetToken(adminEmail, adminPassword);
        assertThat(adminToken).isNotBlank();

        // Step 2: Seed target user in PENDING_VERIFICATION
        String targetEmail = "resident.target@ams.lk";
        String targetPassword = "ResidentPassword123";
        User targetUser = seedUserWithRole(targetEmail, targetPassword, "TENANT_RESIDENT", AccountStatus.PENDING_VERIFICATION);
        UUID targetUserId = targetUser.getId();

        // Step 3: Admin updates target user account status: PENDING_VERIFICATION -> ACTIVE
        UpdateAccountStatusRequest statusRequest = UpdateAccountStatusRequest.builder()
                .status(AccountStatus.ACTIVE)
                .reason(null)
                .build();
        HttpEntity<UpdateAccountStatusRequest> statusEntity = createAuthEntity(statusRequest, adminToken);
        ResponseEntity<AdminUserDetailResponse> statusResponse = restTemplate.exchange(
                "/api/v1/users/" + targetUserId + "/status",
                HttpMethod.PATCH,
                statusEntity,
                AdminUserDetailResponse.class
        );
        assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Step 4: Admin assigns role FINANCE_OFFICER to target user
        AssignRoleRequest roleRequest = AssignRoleRequest.builder()
                .role("FINANCE_OFFICER")
                .build();
        HttpEntity<AssignRoleRequest> roleEntity = createAuthEntity(roleRequest, adminToken);
        ResponseEntity<AdminUserDetailResponse> roleResponse = restTemplate.exchange(
                "/api/v1/users/" + targetUserId + "/roles",
                HttpMethod.POST,
                roleEntity,
                AdminUserDetailResponse.class
        );
        assertThat(roleResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Step 5: Verify records directly in MySQL database via auditEventRepository
        List<AuditEvent> persistedEvents = auditEventRepository.findAll();
        assertThat(persistedEvents).isNotEmpty();

        boolean hasStatusChange = persistedEvents.stream().anyMatch(e ->
                e.getEventType() == AuditEventType.ACCOUNT_STATUS_CHANGED
                        && targetUserId.equals(e.getSubjectUserId())
                        && adminUser.getId().equals(e.getActorUserId())
                        && "PENDING_VERIFICATION".equals(e.getOldValue())
                        && "ACTIVE".equals(e.getNewValue())
        );
        assertThat(hasStatusChange).isTrue();

        boolean hasRoleAssigned = persistedEvents.stream().anyMatch(e ->
                e.getEventType() == AuditEventType.ROLE_ASSIGNED
                        && targetUserId.equals(e.getSubjectUserId())
                        && adminUser.getId().equals(e.getActorUserId())
                        && "FINANCE_OFFICER".equals(e.getNewValue())
        );
        assertThat(hasRoleAssigned).isTrue();

        // Step 6: Query audit logs via GET /api/v1/audit-logs as SYSTEM_ADMINISTRATOR
        HttpEntity<Void> queryEntity = createAuthEntity(null, adminToken);
        ResponseEntity<String> queryResponse = restTemplate.exchange(
                "/api/v1/audit-logs",
                HttpMethod.GET,
                queryEntity,
                String.class
        );

        assertThat(queryResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = objectMapper.readTree(queryResponse.getBody());
        JsonNode content = root.get("content");
        assertThat(content).isNotNull();
        assertThat(content.isArray()).isTrue();
        assertThat(content.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Filtering: Query audit logs filtered by eventType and subjectUserId")
    void testAuditTrailQuery_filteringByEventTypeAndSubjectUserId() throws Exception {
        // Seed admin and user
        String adminEmail = "filter.admin@ams.lk";
        String adminPassword = "AdminPassword123";
        User adminUser = seedAdminUser(adminEmail, adminPassword);
        String adminToken = loginAndGetToken(adminEmail, adminPassword);

        String targetEmail = "filter.target@ams.lk";
        String targetPassword = "TargetPassword123";
        User targetUser = seedUserWithRole(targetEmail, targetPassword, "TENANT_RESIDENT", AccountStatus.ACTIVE);
        UUID targetUserId = targetUser.getId();

        // Admin assigns role
        AssignRoleRequest roleRequest = AssignRoleRequest.builder()
                .role("MAINTENANCE_COORDINATOR")
                .build();
        HttpEntity<AssignRoleRequest> roleEntity = createAuthEntity(roleRequest, adminToken);
        ResponseEntity<AdminUserDetailResponse> roleResponse = restTemplate.exchange(
                "/api/v1/users/" + targetUserId + "/roles",
                HttpMethod.POST,
                roleEntity,
                AdminUserDetailResponse.class
        );
        assertThat(roleResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Query with filters: eventType=ROLE_ASSIGNED and subjectUserId=targetUserId
        HttpEntity<Void> queryEntity = createAuthEntity(null, adminToken);
        ResponseEntity<String> queryResponse = restTemplate.exchange(
                "/api/v1/audit-logs?eventType=ROLE_ASSIGNED&subjectUserId=" + targetUserId,
                HttpMethod.GET,
                queryEntity,
                String.class
        );

        assertThat(queryResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = objectMapper.readTree(queryResponse.getBody());
        JsonNode content = root.get("content");
        assertThat(content.isArray()).isTrue();
        assertThat(content.size()).isEqualTo(1);

        JsonNode record = content.get(0);
        assertThat(record.get("eventType").asText()).isEqualTo("ROLE_ASSIGNED");
        assertThat(record.get("subjectUserId").asText()).isEqualTo(targetUserId.toString());
        assertThat(record.get("actorUserId").asText()).isEqualTo(adminUser.getId().toString());
        assertThat(record.get("newValue").asText()).isEqualTo("MAINTENANCE_COORDINATOR");
    }

    @Test
    @DisplayName("Authorization: Non-admin receives 403 Forbidden and unauthenticated receives 401 Unauthorized")
    void testAuditTrailQuery_nonAdminForbidden_andUnauthenticatedUnauthorized() {
        // Seed regular resident user
        String residentEmail = "regular.resident@ams.lk";
        String residentPassword = "ResidentPassword123";
        seedUserWithRole(residentEmail, residentPassword, "TENANT_RESIDENT", AccountStatus.ACTIVE);
        String residentToken = loginAndGetToken(residentEmail, residentPassword);

        // Attempt GET /api/v1/audit-logs with resident token -> 403 Forbidden
        HttpEntity<Void> residentEntity = createAuthEntity(null, residentToken);
        ResponseEntity<String> forbiddenResponse = restTemplate.exchange(
                "/api/v1/audit-logs",
                HttpMethod.GET,
                residentEntity,
                String.class
        );
        assertThat(forbiddenResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Attempt GET /api/v1/audit-logs without token -> 401 Unauthorized
        ResponseEntity<String> unauthorizedResponse = restTemplate.exchange(
                "/api/v1/audit-logs",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                String.class
        );
        assertThat(unauthorizedResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}

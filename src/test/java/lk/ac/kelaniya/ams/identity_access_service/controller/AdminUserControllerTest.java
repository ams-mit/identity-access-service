package lk.ac.kelaniya.ams.identity_access_service.controller;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.security.SignatureException;
import lk.ac.kelaniya.ams.identity_access_service.dto.request.AdminCreateUserRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.AdminCreateUserResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.AdminUserDetailResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.AdminUserSummaryResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lk.ac.kelaniya.ams.identity_access_service.exception.DuplicateEmailException;
import lk.ac.kelaniya.ams.identity_access_service.exception.GlobalExceptionHandler;
import lk.ac.kelaniya.ams.identity_access_service.exception.InvalidRoleException;
import lk.ac.kelaniya.ams.identity_access_service.exception.RoleAlreadyAssignedException;
import lk.ac.kelaniya.ams.identity_access_service.exception.RoleNotAssignedException;
import lk.ac.kelaniya.ams.identity_access_service.exception.SelfRoleAssignmentException;
import lk.ac.kelaniya.ams.identity_access_service.exception.UserNotFoundException;
import lk.ac.kelaniya.ams.identity_access_service.security.JwtService;
import lk.ac.kelaniya.ams.identity_access_service.security.SecurityConfig;
import lk.ac.kelaniya.ams.identity_access_service.service.AdminUserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminUserController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminUserService adminUserService;

    @MockBean
    private JwtService jwtService;

    private void mockValidToken(String token, UUID userId, String email, List<String> roles) {
        @SuppressWarnings("unchecked")
        Jws<Claims> claimsJws = (Jws<Claims>) mock(Jws.class);
        Claims claims = mock(Claims.class);
        given(claimsJws.getPayload()).willReturn(claims);
        given(claims.getSubject()).willReturn(userId.toString());
        given(claims.get("email", String.class)).willReturn(email);
        given(claims.get("roles", List.class)).willReturn(roles);
        given(jwtService.parseAndValidateToken(token)).willReturn(claimsJws);
    }

    // =========================================================================
    // Authentication & Authorization Tests: GET /api/v1/users
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/users returns 401 when unauthenticated (no Bearer token)")
    void testSearchUsers_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/users returns 401 when Bearer token signature is invalid")
    void testSearchUsers_invalidToken_returns401() throws Exception {
        String token = "invalid.token.signature";
        given(jwtService.parseAndValidateToken(token))
                .willThrow(new SignatureException("JWT signature does not match locally computed signature"));

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/users returns 401 when Bearer token is expired")
    void testSearchUsers_expiredToken_returns401() throws Exception {
        String token = "expired.jwt.token";
        given(jwtService.parseAndValidateToken(token))
                .willThrow(new ExpiredJwtException(null, null, "JWT expired"));

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/users returns 403 when authenticated user has no roles")
    void testSearchUsers_authenticatedNoRoles_returns403() throws Exception {
        String token = "valid.token.noroles";
        UUID userId = UUID.randomUUID();
        mockValidToken(token, userId, "user@example.com", List.of());

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")))
                .andExpect(jsonPath("$.error.message", is("Access denied: insufficient permissions")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "APARTMENT_MANAGER",
            "OWNER",
            "TENANT_RESIDENT",
            "FINANCE_OFFICER",
            "MAINTENANCE_COORDINATOR",
            "TECHNICIAN",
            "SECURITY_OFFICER"
    })
    @DisplayName("GET /api/v1/users returns 403 for all non-admin authenticated roles")
    void testSearchUsers_authenticatedNonAdminRole_returns403(String role) throws Exception {
        String token = "valid.token." + role.toLowerCase();
        UUID userId = UUID.randomUUID();
        mockValidToken(token, userId, "user." + role.toLowerCase() + "@example.com", List.of(role));

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")))
                .andExpect(jsonPath("$.error.message", is("Access denied: insufficient permissions")));
    }

    // =========================================================================
    // Search, Filtering & Pagination Tests: GET /api/v1/users
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/users returns 200 with paginated user list when authenticated as SYSTEM_ADMINISTRATOR")
    void testSearchUsers_systemAdministrator_succeeds() throws Exception {
        String token = "valid.sysadmin.token";
        UUID adminId = UUID.randomUUID();
        mockValidToken(token, adminId, "admin@ams.lk", List.of("SYSTEM_ADMINISTRATOR"));

        UUID u1 = UUID.randomUUID();
        AdminUserSummaryResponse user1 = AdminUserSummaryResponse.builder()
                .userId(u1)
                .email("owner1@ams.lk")
                .fullName("Kamal Perera")
                .accountStatus(AccountStatus.PENDING_VERIFICATION)
                .requestedRole("OWNER")
                .roles(List.of())
                .build();

        Page<AdminUserSummaryResponse> mockPage = new PageImpl<>(List.of(user1), PageRequest.of(0, 20), 1);
        given(adminUserService.searchUsers(any(), any(), any(), any(Pageable.class))).willReturn(mockPage);

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].userId", is(u1.toString())))
                .andExpect(jsonPath("$.content[0].email", is("owner1@ams.lk")))
                .andExpect(jsonPath("$.content[0].fullName", is("Kamal Perera")))
                .andExpect(jsonPath("$.content[0].accountStatus", is("PENDING_VERIFICATION")))
                .andExpect(jsonPath("$.content[0].requestedRole", is("OWNER")))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    @DisplayName("GET /api/v1/users filters by requestedRole for batch review workflow")
    void testSearchUsers_filterByRequestedRole() throws Exception {
        String token = "valid.sysadmin.token";
        UUID adminId = UUID.randomUUID();
        mockValidToken(token, adminId, "admin@ams.lk", List.of("SYSTEM_ADMINISTRATOR"));

        UUID u1 = UUID.randomUUID();
        AdminUserSummaryResponse user1 = AdminUserSummaryResponse.builder()
                .userId(u1)
                .email("tech1@ams.lk")
                .fullName("Sunil Silva")
                .accountStatus(AccountStatus.PENDING_VERIFICATION)
                .requestedRole("TECHNICIAN")
                .roles(List.of())
                .build();

        Page<AdminUserSummaryResponse> mockPage = new PageImpl<>(List.of(user1), PageRequest.of(0, 20), 1);
        given(adminUserService.searchUsers(eq(null), eq(null), eq("TECHNICIAN"), any(Pageable.class)))
                .willReturn(mockPage);

        mockMvc.perform(get("/api/v1/users")
                        .param("requestedRole", "TECHNICIAN")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].requestedRole", is("TECHNICIAN")));

        verify(adminUserService).searchUsers(eq(null), eq(null), eq("TECHNICIAN"), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/v1/users filters by status")
    void testSearchUsers_filterByStatus() throws Exception {
        String token = "valid.sysadmin.token";
        UUID adminId = UUID.randomUUID();
        mockValidToken(token, adminId, "admin@ams.lk", List.of("SYSTEM_ADMINISTRATOR"));

        Page<AdminUserSummaryResponse> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        given(adminUserService.searchUsers(eq(null), eq(AccountStatus.SUSPENDED), eq(null), any(Pageable.class)))
                .willReturn(emptyPage);

        mockMvc.perform(get("/api/v1/users")
                        .param("status", "SUSPENDED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements", is(0)));

        verify(adminUserService).searchUsers(eq(null), eq(AccountStatus.SUSPENDED), eq(null), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/v1/users combines query, status, and requestedRole filters")
    void testSearchUsers_combinesAllFilters() throws Exception {
        String token = "valid.sysadmin.token";
        UUID adminId = UUID.randomUUID();
        mockValidToken(token, adminId, "admin@ams.lk", List.of("SYSTEM_ADMINISTRATOR"));

        UUID u1 = UUID.randomUUID();
        AdminUserSummaryResponse user1 = AdminUserSummaryResponse.builder()
                .userId(u1)
                .email("kamal@ams.lk")
                .fullName("Kamal Silva")
                .accountStatus(AccountStatus.PENDING_VERIFICATION)
                .requestedRole("OWNER")
                .roles(List.of())
                .build();

        Page<AdminUserSummaryResponse> mockPage = new PageImpl<>(List.of(user1), PageRequest.of(0, 20), 1);
        given(adminUserService.searchUsers(eq("kamal"), eq(AccountStatus.PENDING_VERIFICATION), eq("OWNER"), any(Pageable.class)))
                .willReturn(mockPage);

        mockMvc.perform(get("/api/v1/users")
                        .param("query", "kamal")
                        .param("status", "PENDING_VERIFICATION")
                        .param("requestedRole", "OWNER")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].email", is("kamal@ams.lk")));

        verify(adminUserService).searchUsers(eq("kamal"), eq(AccountStatus.PENDING_VERIFICATION), eq("OWNER"), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/v1/users respects pagination parameters across multiple pages")
    void testSearchUsers_respectsPaginationParams() throws Exception {
        String token = "valid.sysadmin.token";
        UUID adminId = UUID.randomUUID();
        mockValidToken(token, adminId, "admin@ams.lk", List.of("SYSTEM_ADMINISTRATOR"));

        List<AdminUserSummaryResponse> secondPageUsers = List.of(
                AdminUserSummaryResponse.builder().userId(UUID.randomUUID()).email("u3@ams.lk").fullName("User 3").accountStatus(AccountStatus.ACTIVE).requestedRole("OWNER").build(),
                AdminUserSummaryResponse.builder().userId(UUID.randomUUID()).email("u4@ams.lk").fullName("User 4").accountStatus(AccountStatus.ACTIVE).requestedRole("OWNER").build()
        );

        Page<AdminUserSummaryResponse> mockPage = new PageImpl<>(secondPageUsers, PageRequest.of(1, 2), 6);
        given(adminUserService.searchUsers(any(), any(), any(), any(Pageable.class))).willReturn(mockPage);

        mockMvc.perform(get("/api/v1/users")
                        .param("page", "1")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements", is(6)))
                .andExpect(jsonPath("$.totalPages", is(3)))
                .andExpect(jsonPath("$.number", is(1)))
                .andExpect(jsonPath("$.size", is(2)));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(adminUserService).searchUsers(any(), any(), any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(2);
    }

    // =========================================================================
    // User Detail Endpoint Tests: GET /api/v1/users/{userId}
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/users/{userId} returns 401 when unauthenticated")
    void testGetUserById_unauthenticated_returns401() throws Exception {
        UUID targetId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/users/{userId}", targetId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/users/{userId} returns 403 for non-admin role")
    void testGetUserById_nonAdmin_returns403() throws Exception {
        UUID targetId = UUID.randomUUID();
        String token = "valid.token.tenant";
        mockValidToken(token, UUID.randomUUID(), "tenant@example.com", List.of("TENANT_RESIDENT"));

        mockMvc.perform(get("/api/v1/users/{userId}", targetId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("GET /api/v1/users/{userId} returns 200 with full record including requestedRole for existing user")
    void testGetUserById_existingUser_returns200() throws Exception {
        UUID targetId = UUID.randomUUID();
        String token = "valid.sysadmin.token";
        mockValidToken(token, UUID.randomUUID(), "admin@ams.lk", List.of("SYSTEM_ADMINISTRATOR"));

        AdminUserDetailResponse detail = AdminUserDetailResponse.builder()
                .userId(targetId)
                .username("applicant_john")
                .email("applicant.john@example.com")
                .fullName("John Applicant")
                .firstName("John")
                .lastName("Applicant")
                .phone("+94712345678")
                .accountStatus(AccountStatus.PENDING_VERIFICATION)
                .requestedRole("OWNER")
                .roles(List.of())
                .failedAttemptCount(0)
                .lockedUntil(null)
                .accountLocked(false)
                .createdAt(Instant.parse("2026-09-13T08:00:00Z"))
                .updatedAt(Instant.parse("2026-09-13T08:00:00Z"))
                .build();

        given(adminUserService.getUserById(targetId)).willReturn(detail);

        mockMvc.perform(get("/api/v1/users/{userId}", targetId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(targetId.toString())))
                .andExpect(jsonPath("$.username", is("applicant_john")))
                .andExpect(jsonPath("$.email", is("applicant.john@example.com")))
                .andExpect(jsonPath("$.fullName", is("John Applicant")))
                .andExpect(jsonPath("$.firstName", is("John")))
                .andExpect(jsonPath("$.lastName", is("Applicant")))
                .andExpect(jsonPath("$.phone", is("+94712345678")))
                .andExpect(jsonPath("$.accountStatus", is("PENDING_VERIFICATION")))
                .andExpect(jsonPath("$.requestedRole", is("OWNER")))
                .andExpect(jsonPath("$.roles", hasSize(0)))
                .andExpect(jsonPath("$.grantedRoles", hasSize(0)))
                .andExpect(jsonPath("$.failedAttemptCount", is(0)))
                .andExpect(jsonPath("$.accountLocked", is(false)))
                .andExpect(jsonPath("$.createdAt", is("2026-09-13T08:00:00Z")))
                .andExpect(jsonPath("$.updatedAt", is("2026-09-13T08:00:00Z")));
    }

    @Test
    @DisplayName("GET /api/v1/users/{userId} returns 404 when user does not exist")
    void testGetUserById_nonExistingUser_returns404() throws Exception {
        UUID missingId = UUID.randomUUID();
        String token = "valid.sysadmin.token";
        mockValidToken(token, UUID.randomUUID(), "admin@ams.lk", List.of("SYSTEM_ADMINISTRATOR"));

        given(adminUserService.getUserById(missingId))
                .willThrow(new UserNotFoundException("User not found with id: " + missingId));

        mockMvc.perform(get("/api/v1/users/{userId}", missingId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("USER_NOT_FOUND")))
                .andExpect(jsonPath("$.error.message", is("User not found with id: " + missingId)));
    }

    // =========================================================================
    // Account Status Transition Tests: PATCH /api/v1/users/{userId}/status
    // =========================================================================

    @Test
    @DisplayName("PATCH /api/v1/users/{userId}/status returns 401 when unauthenticated")
    void testUpdateAccountStatus_unauthenticated_returns401() throws Exception {
        UUID targetId = UUID.randomUUID();
        String json = "{\"status\":\"ACTIVE\"}";

        mockMvc.perform(patch("/api/v1/users/{userId}/status", targetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PATCH /api/v1/users/{userId}/status returns 403 for non-admin roles")
    void testUpdateAccountStatus_nonAdmin_returns403() throws Exception {
        UUID targetId = UUID.randomUUID();
        String token = "valid.token.manager";
        mockValidToken(token, UUID.randomUUID(), "manager@ams.lk", List.of("APARTMENT_MANAGER"));
        String json = "{\"status\":\"ACTIVE\"}";

        mockMvc.perform(patch("/api/v1/users/{userId}/status", targetId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/{userId}/status returns 404 when target user does not exist")
    void testUpdateAccountStatus_userNotFound_returns404() throws Exception {
        UUID missingId = UUID.randomUUID();
        String token = "valid.sysadmin.token";
        UUID adminId = UUID.randomUUID();
        mockValidToken(token, adminId, "admin@ams.lk", List.of("SYSTEM_ADMINISTRATOR"));

        given(adminUserService.updateAccountStatus(eq(missingId), any(), any()))
                .willThrow(new UserNotFoundException("User not found with id: " + missingId));

        String json = "{\"status\":\"ACTIVE\"}";

        mockMvc.perform(patch("/api/v1/users/{userId}/status", missingId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("USER_NOT_FOUND")));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/{userId}/status returns 400 when transition is invalid")
    void testUpdateAccountStatus_invalidTransition_returns400() throws Exception {
        UUID targetId = UUID.randomUUID();
        String token = "valid.sysadmin.token";
        UUID adminId = UUID.randomUUID();
        mockValidToken(token, adminId, "admin@ams.lk", List.of("SYSTEM_ADMINISTRATOR"));

        given(adminUserService.updateAccountStatus(eq(targetId), any(), any()))
                .willThrow(new lk.ac.kelaniya.ams.identity_access_service.exception.InvalidStatusTransitionException(
                        "Invalid account status transition from REJECTED to ACTIVE."
                ));

        String json = "{\"status\":\"ACTIVE\"}";

        mockMvc.perform(patch("/api/v1/users/{userId}/status", targetId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_STATUS_TRANSITION")))
                .andExpect(jsonPath("$.error.message", is("Invalid account status transition from REJECTED to ACTIVE.")));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/{userId}/status returns 400 when reason is missing for SUSPENDED")
    void testUpdateAccountStatus_missingReason_returns400() throws Exception {
        UUID targetId = UUID.randomUUID();
        String token = "valid.sysadmin.token";
        UUID adminId = UUID.randomUUID();
        mockValidToken(token, adminId, "admin@ams.lk", List.of("SYSTEM_ADMINISTRATOR"));

        given(adminUserService.updateAccountStatus(eq(targetId), any(), any()))
                .willThrow(new lk.ac.kelaniya.ams.identity_access_service.exception.InvalidStatusTransitionException(
                        "Reason is required when transitioning account status to SUSPENDED."
                ));

        String json = "{\"status\":\"SUSPENDED\"}";

        mockMvc.perform(patch("/api/v1/users/{userId}/status", targetId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_STATUS_TRANSITION")))
                .andExpect(jsonPath("$.error.message", is("Reason is required when transitioning account status to SUSPENDED.")));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/{userId}/status returns 200 for valid transition to ACTIVE without reason")
    void testUpdateAccountStatus_pendingToActive_succeedsWithoutReason() throws Exception {
        UUID targetId = UUID.randomUUID();
        String token = "valid.sysadmin.token";
        UUID adminId = UUID.randomUUID();
        mockValidToken(token, adminId, "admin@ams.lk", List.of("SYSTEM_ADMINISTRATOR"));

        AdminUserDetailResponse detail = AdminUserDetailResponse.builder()
                .userId(targetId)
                .email("user@ams.lk")
                .fullName("John Doe")
                .accountStatus(AccountStatus.ACTIVE)
                .requestedRole("OWNER")
                .roles(List.of())
                .build();

        given(adminUserService.updateAccountStatus(eq(targetId), any(), any())).willReturn(detail);

        String json = "{\"status\":\"ACTIVE\"}";

        mockMvc.perform(patch("/api/v1/users/{userId}/status", targetId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(targetId.toString())))
                .andExpect(jsonPath("$.accountStatus", is("ACTIVE")))
                .andExpect(jsonPath("$.requestedRole", is("OWNER")));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/{userId}/status returns 200 for valid transition to REJECTED with reason")
    void testUpdateAccountStatus_pendingToRejected_succeedsWithReason() throws Exception {
        UUID targetId = UUID.randomUUID();
        String token = "valid.sysadmin.token";
        UUID adminId = UUID.randomUUID();
        mockValidToken(token, adminId, "admin@ams.lk", List.of("SYSTEM_ADMINISTRATOR"));

        AdminUserDetailResponse detail = AdminUserDetailResponse.builder()
                .userId(targetId)
                .email("fraud@ams.lk")
                .fullName("Fraud User")
                .accountStatus(AccountStatus.REJECTED)
                .requestedRole("TENANT_RESIDENT")
                .roles(List.of())
                .build();

        given(adminUserService.updateAccountStatus(eq(targetId), any(), any())).willReturn(detail);

        String json = "{\"status\":\"REJECTED\",\"reason\":\"Forged lease documents\"}";

        mockMvc.perform(patch("/api/v1/users/{userId}/status", targetId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(targetId.toString())))
                .andExpect(jsonPath("$.accountStatus", is("REJECTED")))
                .andExpect(jsonPath("$.requestedRole", is("TENANT_RESIDENT")));
    }

    // =========================================================================
    // Role Assignment Tests: POST /api/v1/users/{userId}/roles
    // =========================================================================

    @Test
    @DisplayName("POST /api/v1/users/{userId}/roles returns 401 when unauthenticated")
    void testAssignRole_unauthenticated_returns401() throws Exception {
        UUID targetId = UUID.randomUUID();
        String json = "{\"role\":\"FINANCE_OFFICER\"}";

        mockMvc.perform(post("/api/v1/users/{userId}/roles", targetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/users/{userId}/roles returns 403 for non-admin roles")
    void testAssignRole_nonAdmin_returns403() throws Exception {
        UUID targetId = UUID.randomUUID();
        String token = "valid.manager.token";
        mockValidToken(token, UUID.randomUUID(), "manager@ams.lk", List.of("APARTMENT_MANAGER"));
        String json = "{\"role\":\"FINANCE_OFFICER\"}";

        mockMvc.perform(post("/api/v1/users/{userId}/roles", targetId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("POST /api/v1/users/{userId}/roles returns 403 when admin attempts self-assignment")
    void testAssignRole_selfAssignment_returns403() throws Exception {
        UUID adminId = UUID.randomUUID();
        String token = "valid.sysadmin.token";
        mockValidToken(token, adminId, "admin@ams.lk", List.of("SYSTEM_ADMINISTRATOR"));
        String json = "{\"role\":\"FINANCE_OFFICER\"}";

        mockMvc.perform(post("/api/v1/users/{userId}/roles", adminId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")))
                .andExpect(jsonPath("$.error.message", is("Administrators cannot assign roles to their own account.")));
    }

    @Test
    @DisplayName("POST /api/v1/users/{userId}/roles returns 200 on successful role grant")
    void testAssignRole_success_returns200() throws Exception {
        UUID targetId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        String token = "valid.sysadmin.token";
        mockValidToken(token, adminId, "admin@ams.lk", List.of("SYSTEM_ADMINISTRATOR"));

        AdminUserDetailResponse detail = AdminUserDetailResponse.builder()
                .userId(targetId)
                .email("user@ams.lk")
                .fullName("Jane Doe")
                .accountStatus(AccountStatus.ACTIVE)
                .requestedRole("TENANT_RESIDENT")
                .roles(List.of("FINANCE_OFFICER"))
                .build();

        given(adminUserService.assignRole(eq(targetId), eq("FINANCE_OFFICER"), eq(adminId))).willReturn(detail);

        String json = "{\"role\":\"FINANCE_OFFICER\"}";

        mockMvc.perform(post("/api/v1/users/{userId}/roles", targetId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(targetId.toString())))
                .andExpect(jsonPath("$.roles[0]", is("FINANCE_OFFICER")));
    }

    @Test
    @DisplayName("POST /api/v1/users/{userId}/roles returns 400 when role name is blank or invalid")
    void testAssignRole_invalidRole_returns400() throws Exception {
        UUID targetId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        String token = "valid.sysadmin.token";
        mockValidToken(token, adminId, "admin@ams.lk", List.of("SYSTEM_ADMINISTRATOR"));

        // Case 1: blank role fails @NotBlank validation
        String blankJson = "{\"role\":\"\"}";
        mockMvc.perform(post("/api/v1/users/{userId}/roles", targetId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blankJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")));

        // Case 2: service throws InvalidRoleException
        given(adminUserService.assignRole(eq(targetId), eq("UNKNOWN_ROLE"), eq(adminId)))
                .willThrow(new InvalidRoleException("Invalid role: 'UNKNOWN_ROLE'. Valid roles are: ..."));

        String unknownRoleJson = "{\"role\":\"UNKNOWN_ROLE\"}";
        mockMvc.perform(post("/api/v1/users/{userId}/roles", targetId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unknownRoleJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_ROLE")));
    }

    @Test
    @DisplayName("POST /api/v1/users/{userId}/roles returns 409 when user already holds the role")
    void testAssignRole_alreadyHeld_returns409() throws Exception {
        UUID targetId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        String token = "valid.sysadmin.token";
        mockValidToken(token, adminId, "admin@ams.lk", List.of("SYSTEM_ADMINISTRATOR"));

        given(adminUserService.assignRole(eq(targetId), eq("FINANCE_OFFICER"), eq(adminId)))
                .willThrow(new RoleAlreadyAssignedException("User already holds the role: FINANCE_OFFICER"));

        String json = "{\"role\":\"FINANCE_OFFICER\"}";

        mockMvc.perform(post("/api/v1/users/{userId}/roles", targetId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("ROLE_ALREADY_ASSIGNED")))
                .andExpect(jsonPath("$.error.message", is("User already holds the role: FINANCE_OFFICER")));
    }

    @Test
    @DisplayName("POST /api/v1/users/{userId}/roles returns 404 when target user does not exist")
    void testAssignRole_userNotFound_returns404() throws Exception {
        UUID missingId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        String token = "valid.sysadmin.token";
        mockValidToken(token, adminId, "admin@ams.lk", List.of("SYSTEM_ADMINISTRATOR"));

        given(adminUserService.assignRole(eq(missingId), eq("FINANCE_OFFICER"), eq(adminId)))
                .willThrow(new UserNotFoundException("User not found with id: " + missingId));

        String json = "{\"role\":\"FINANCE_OFFICER\"}";

        mockMvc.perform(post("/api/v1/users/{userId}/roles", missingId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("USER_NOT_FOUND")));
    }

    // =========================================================================
    // Role Removal Tests: DELETE /api/v1/users/{userId}/roles/{roleName}
    // =========================================================================

    @Test
    @DisplayName("DELETE /api/v1/users/{userId}/roles/{roleName} returns 401 when unauthenticated")
    void testRemoveRole_unauthenticated_returns401() throws Exception {
        UUID targetId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/users/{userId}/roles/{roleName}", targetId, "FINANCE_OFFICER"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /api/v1/users/{userId}/roles/{roleName} returns 403 for non-admin roles")
    void testRemoveRole_nonAdmin_returns403() throws Exception {
        UUID targetId = UUID.randomUUID();
        String token = "valid.tenant.token";
        mockValidToken(token, UUID.randomUUID(), "tenant@ams.lk", List.of("TENANT_RESIDENT"));

        mockMvc.perform(delete("/api/v1/users/{userId}/roles/{roleName}", targetId, "FINANCE_OFFICER")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("DELETE /api/v1/users/{userId}/roles/{roleName} returns 403 when admin attempts self-removal")
    void testRemoveRole_selfRemoval_returns403() throws Exception {
        UUID adminId = UUID.randomUUID();
        String token = "valid.sysadmin.token";
        mockValidToken(token, adminId, "admin@ams.lk", List.of("SYSTEM_ADMINISTRATOR"));

        mockMvc.perform(delete("/api/v1/users/{userId}/roles/{roleName}", adminId, "SYSTEM_ADMINISTRATOR")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")))
                .andExpect(jsonPath("$.error.message", is("Administrators cannot remove roles from their own account.")));
    }

    @Test
    @DisplayName("DELETE /api/v1/users/{userId}/roles/{roleName} returns 200 on successful role removal")
    void testRemoveRole_success_returns200() throws Exception {
        UUID targetId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        String token = "valid.sysadmin.token";
        mockValidToken(token, adminId, "admin@ams.lk", List.of("SYSTEM_ADMINISTRATOR"));

        AdminUserDetailResponse detail = AdminUserDetailResponse.builder()
                .userId(targetId)
                .email("user@ams.lk")
                .fullName("Jane Doe")
                .accountStatus(AccountStatus.ACTIVE)
                .requestedRole("TENANT_RESIDENT")
                .roles(List.of())
                .build();

        given(adminUserService.removeRole(eq(targetId), eq("FINANCE_OFFICER"), eq(adminId))).willReturn(detail);

        mockMvc.perform(delete("/api/v1/users/{userId}/roles/{roleName}", targetId, "FINANCE_OFFICER")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(targetId.toString())))
                .andExpect(jsonPath("$.roles", hasSize(0)));
    }

    @Test
    @DisplayName("DELETE /api/v1/users/{userId}/roles/{roleName} returns 400 when role name is invalid")
    void testRemoveRole_invalidRole_returns400() throws Exception {
        UUID targetId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        String token = "valid.sysadmin.token";
        mockValidToken(token, adminId, "admin@ams.lk", List.of("SYSTEM_ADMINISTRATOR"));

        given(adminUserService.removeRole(eq(targetId), eq("INVALID_ROLE"), eq(adminId)))
                .willThrow(new InvalidRoleException("Invalid role: 'INVALID_ROLE'. Valid roles are: ..."));

        mockMvc.perform(delete("/api/v1/users/{userId}/roles/{roleName}", targetId, "INVALID_ROLE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_ROLE")));
    }

    @Test
    @DisplayName("DELETE /api/v1/users/{userId}/roles/{roleName} returns 409 when user does not hold the role")
    void testRemoveRole_notHeld_returns409() throws Exception {
        UUID targetId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        String token = "valid.sysadmin.token";
        mockValidToken(token, adminId, "admin@ams.lk", List.of("SYSTEM_ADMINISTRATOR"));

        given(adminUserService.removeRole(eq(targetId), eq("FINANCE_OFFICER"), eq(adminId)))
                .willThrow(new RoleNotAssignedException("User does not hold the role: FINANCE_OFFICER"));

        mockMvc.perform(delete("/api/v1/users/{userId}/roles/{roleName}", targetId, "FINANCE_OFFICER")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("ROLE_NOT_ASSIGNED")))
                .andExpect(jsonPath("$.error.message", is("User does not hold the role: FINANCE_OFFICER")));
    }

    @Test
    @DisplayName("DELETE /api/v1/users/{userId}/roles/{roleName} returns 404 when target user does not exist")
    void testRemoveRole_userNotFound_returns404() throws Exception {
        UUID missingId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        String token = "valid.sysadmin.token";
        mockValidToken(token, adminId, "admin@ams.lk", List.of("SYSTEM_ADMINISTRATOR"));

        given(adminUserService.removeRole(eq(missingId), eq("FINANCE_OFFICER"), eq(adminId)))
                .willThrow(new UserNotFoundException("User not found with id: " + missingId));

        mockMvc.perform(delete("/api/v1/users/{userId}/roles/{roleName}", missingId, "FINANCE_OFFICER")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("USER_NOT_FOUND")));
    }

    @Test
    @DisplayName("POST /api/v1/users returns 201 with correct fields and does NOT leak temporary password")
    void testCreateUser_success_returns201AndDoesNotLeakTemporaryPassword() throws Exception {
        UUID newUserId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        String token = "valid.sysadmin.token";
        mockValidToken(token, adminId, "admin@ams.lk", List.of("SYSTEM_ADMINISTRATOR"));

        AdminCreateUserResponse response = AdminCreateUserResponse.builder()
                .userId(newUserId)
                .email("jane.doe@ams.lk")
                .accountStatus(AccountStatus.ACTIVE)
                .mustChangePassword(true)
                .build();

        given(adminUserService.createUser(any(AdminCreateUserRequest.class), any())).willReturn(response);

        String json = "{"
                + "\"firstName\":\"Jane\","
                + "\"lastName\":\"Doe\","
                + "\"email\":\"jane.doe@ams.lk\","
                + "\"phone\":\"+94771234567\","
                + "\"temporaryPassword\":\"TempSecret123\""
                + "}";

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId", is(newUserId.toString())))
                .andExpect(jsonPath("$.email", is("jane.doe@ams.lk")))
                .andExpect(jsonPath("$.accountStatus", is("ACTIVE")))
                .andExpect(jsonPath("$.mustChangePassword", is(true)))
                .andExpect(content().string(not(containsString("TempSecret123"))));
    }

    @Test
    @DisplayName("POST /api/v1/users returns 409 Conflict when email is already registered")
    void testCreateUser_duplicateEmail_returns409() throws Exception {
        UUID adminId = UUID.randomUUID();
        String token = "valid.sysadmin.token";
        mockValidToken(token, adminId, "admin@ams.lk", List.of("SYSTEM_ADMINISTRATOR"));

        given(adminUserService.createUser(any(AdminCreateUserRequest.class), any()))
                .willThrow(new DuplicateEmailException("Email already in use"));

        String json = "{"
                + "\"firstName\":\"Jane\","
                + "\"lastName\":\"Doe\","
                + "\"email\":\"duplicate@ams.lk\","
                + "\"phone\":\"+94771234567\","
                + "\"temporaryPassword\":\"TempSecret123\""
                + "}";

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("EMAIL_ALREADY_EXISTS")))
                .andExpect(jsonPath("$.error.message", is("Email already in use")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Short1",            // too short (< 8 chars)
            "NoDigitsHereAtAll", // missing numeric digit
            "",                  // blank password
            "   "                // whitespace password
    })
    @DisplayName("POST /api/v1/users returns 400 Bad Request when temporary password violates password policy")
    void testCreateUser_invalidTemporaryPassword_returns400(String weakPassword) throws Exception {
        UUID adminId = UUID.randomUUID();
        String token = "valid.sysadmin.token";
        mockValidToken(token, adminId, "admin@ams.lk", List.of("SYSTEM_ADMINISTRATOR"));

        String json = "{"
                + "\"firstName\":\"Jane\","
                + "\"lastName\":\"Doe\","
                + "\"email\":\"jane.doe@ams.lk\","
                + "\"phone\":\"+94771234567\","
                + "\"temporaryPassword\":\"" + weakPassword + "\""
                + "}";

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("POST /api/v1/users returns 400 Bad Request when required fields are missing or invalid")
    void testCreateUser_missingRequiredFields_returns400() throws Exception {
        UUID adminId = UUID.randomUUID();
        String token = "valid.sysadmin.token";
        mockValidToken(token, adminId, "admin@ams.lk", List.of("SYSTEM_ADMINISTRATOR"));

        String json = "{"
                + "\"firstName\":\"\","
                + "\"lastName\":\"\","
                + "\"email\":\"not-an-email\","
                + "\"temporaryPassword\":\"TempSecret123\""
                + "}";

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("POST /api/v1/users returns 403 Forbidden when caller is not SYSTEM_ADMINISTRATOR")
    void testCreateUser_nonAdmin_returns403() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = "valid.nonadmin.token";
        mockValidToken(token, userId, "user@ams.lk", List.of("TENANT_RESIDENT"));

        String json = "{"
                + "\"firstName\":\"Jane\","
                + "\"lastName\":\"Doe\","
                + "\"email\":\"jane.doe@ams.lk\","
                + "\"temporaryPassword\":\"TempSecret123\""
                + "}";

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("POST /api/v1/users returns 401 Unauthorized when request is unauthenticated")
    void testCreateUser_unauthenticated_returns401() throws Exception {
        String json = "{"
                + "\"firstName\":\"Jane\","
                + "\"lastName\":\"Doe\","
                + "\"email\":\"jane.doe@ams.lk\","
                + "\"temporaryPassword\":\"TempSecret123\""
                + "}";

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnauthorized());
    }
}

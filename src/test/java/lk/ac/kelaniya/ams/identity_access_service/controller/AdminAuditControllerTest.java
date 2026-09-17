package lk.ac.kelaniya.ams.identity_access_service.controller;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.AuditEventResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.AuditEventType;
import lk.ac.kelaniya.ams.identity_access_service.exception.GlobalExceptionHandler;
import lk.ac.kelaniya.ams.identity_access_service.security.JwtService;
import lk.ac.kelaniya.ams.identity_access_service.security.SecurityConfig;
import lk.ac.kelaniya.ams.identity_access_service.service.AuditService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminAuditController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AdminAuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditService auditService;

    @MockBean
    private JwtService jwtService;

    private void mockValidUserToken(String token, UUID userId, String email, List<String> roles) {
        @SuppressWarnings("unchecked")
        Jws<Claims> claimsJws = (Jws<Claims>) mock(Jws.class);
        Claims claims = mock(Claims.class);
        given(claimsJws.getPayload()).willReturn(claims);
        given(claims.get("type", String.class)).willReturn("user");
        given(claims.getSubject()).willReturn(userId.toString());
        given(claims.get("email", String.class)).willReturn(email);
        given(claims.get("roles", List.class)).willReturn(roles);
        given(jwtService.parseAndValidateToken(token)).willReturn(claimsJws);
    }

    @Test
    @DisplayName("GET /api/v1/audit-logs as SYSTEM_ADMINISTRATOR returns 200 and paginated audit list")
    void testGetAuditLogs_asSystemAdministrator_returns200() throws Exception {
        UUID adminId = UUID.randomUUID();
        String token = "valid.admin.jwt.token";
        mockValidUserToken(token, adminId, "admin@ams.lk", List.of("SYSTEM_ADMINISTRATOR"));

        UUID subjectId = UUID.randomUUID();
        UUID auditId = UUID.randomUUID();
        Instant now = Instant.now();

        AuditEventResponse item = AuditEventResponse.builder()
                .id(auditId)
                .eventType(AuditEventType.ACCOUNT_STATUS_CHANGED)
                .subjectUserId(subjectId)
                .actorUserId(adminId)
                .oldValue("PENDING_VERIFICATION")
                .newValue("ACTIVE")
                .reason("Verified")
                .createdAt(now)
                .build();

        Page<AuditEventResponse> page = new PageImpl<>(List.of(item));
        given(auditService.searchAuditLogs(any(), any(), any(), any(), any(), any(Pageable.class)))
                .willReturn(page);

        mockMvc.perform(get("/api/v1/audit-logs")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(auditId.toString())))
                .andExpect(jsonPath("$.content[0].eventType", is("ACCOUNT_STATUS_CHANGED")))
                .andExpect(jsonPath("$.content[0].subjectUserId", is(subjectId.toString())))
                .andExpect(jsonPath("$.content[0].actorUserId", is(adminId.toString())))
                .andExpect(jsonPath("$.content[0].oldValue", is("PENDING_VERIFICATION")))
                .andExpect(jsonPath("$.content[0].newValue", is("ACTIVE")));
    }

    @Test
    @DisplayName("GET /api/v1/audit-logs passes query parameters to service")
    void testGetAuditLogs_passesFiltersToService() throws Exception {
        UUID adminId = UUID.randomUUID();
        String token = "valid.admin.jwt.token";
        mockValidUserToken(token, adminId, "admin@ams.lk", List.of("SYSTEM_ADMINISTRATOR"));

        UUID subjectId = UUID.randomUUID();
        Page<AuditEventResponse> emptyPage = new PageImpl<>(List.of());
        given(auditService.searchAuditLogs(eq(AuditEventType.ROLE_ASSIGNED), eq(subjectId), any(), any(), any(), any(Pageable.class)))
                .willReturn(emptyPage);

        mockMvc.perform(get("/api/v1/audit-logs")
                        .header("Authorization", "Bearer " + token)
                        .param("eventType", "ROLE_ASSIGNED")
                        .param("subjectUserId", subjectId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(auditService).searchAuditLogs(
                eq(AuditEventType.ROLE_ASSIGNED),
                eq(subjectId),
                any(),
                any(),
                any(),
                any(Pageable.class)
        );
    }

    @Test
    @DisplayName("GET /api/v1/audit-logs?eventType=ROLE_ASSIGNED returns ONLY matching eventType events")
    void testGetAuditLogs_filterByEventType_returnsOnlyMatchingEvents() throws Exception {
        UUID adminId = UUID.randomUUID();
        String token = "valid.admin.jwt.token";
        mockValidUserToken(token, adminId, "admin@ams.lk", List.of("SYSTEM_ADMINISTRATOR"));

        UUID auditId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        AuditEventResponse matchingItem = AuditEventResponse.builder()
                .id(auditId)
                .eventType(AuditEventType.ROLE_ASSIGNED)
                .subjectUserId(subjectId)
                .actorUserId(adminId)
                .newValue("FINANCE_OFFICER")
                .createdAt(Instant.now())
                .build();

        Page<AuditEventResponse> page = new PageImpl<>(List.of(matchingItem));
        given(auditService.searchAuditLogs(eq(AuditEventType.ROLE_ASSIGNED), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .willReturn(page);

        mockMvc.perform(get("/api/v1/audit-logs")
                        .header("Authorization", "Bearer " + token)
                        .param("eventType", "ROLE_ASSIGNED")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(auditId.toString())))
                .andExpect(jsonPath("$.content[0].eventType", is("ROLE_ASSIGNED")))
                .andExpect(jsonPath("$.content[0].newValue", is("FINANCE_OFFICER")));

        verify(auditService).searchAuditLogs(
                eq(AuditEventType.ROLE_ASSIGNED),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                any(Pageable.class)
        );
    }

    @Test
    @DisplayName("GET /api/v1/audit-logs?subjectUserId=... returns ONLY that user's events")
    void testGetAuditLogs_filterBySubjectUserId_returnsOnlyThatUsersEvents() throws Exception {
        UUID adminId = UUID.randomUUID();
        String token = "valid.admin.jwt.token";
        mockValidUserToken(token, adminId, "admin@ams.lk", List.of("SYSTEM_ADMINISTRATOR"));

        UUID targetSubjectId = UUID.randomUUID();
        UUID auditId = UUID.randomUUID();
        AuditEventResponse userItem = AuditEventResponse.builder()
                .id(auditId)
                .eventType(AuditEventType.PASSWORD_CHANGED)
                .subjectUserId(targetSubjectId)
                .actorUserId(targetSubjectId)
                .createdAt(Instant.now())
                .build();

        Page<AuditEventResponse> page = new PageImpl<>(List.of(userItem));
        given(auditService.searchAuditLogs(isNull(), eq(targetSubjectId), isNull(), isNull(), isNull(), any(Pageable.class)))
                .willReturn(page);

        mockMvc.perform(get("/api/v1/audit-logs")
                        .header("Authorization", "Bearer " + token)
                        .param("subjectUserId", targetSubjectId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(auditId.toString())))
                .andExpect(jsonPath("$.content[0].subjectUserId", is(targetSubjectId.toString())));

        verify(auditService).searchAuditLogs(
                isNull(),
                eq(targetSubjectId),
                isNull(),
                isNull(),
                isNull(),
                any(Pageable.class)
        );
    }

    @Test
    @DisplayName("GET /api/v1/audit-logs as non-admin user returns 403 FORBIDDEN")
    void testGetAuditLogs_asRegularUser_returns403Forbidden() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = "valid.resident.jwt.token";
        mockValidUserToken(token, userId, "resident@ams.lk", List.of("TENANT_RESIDENT"));

        mockMvc.perform(get("/api/v1/audit-logs")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/audit-logs unauthenticated returns 401 UNAUTHORIZED")
    void testGetAuditLogs_unauthenticated_returns401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}

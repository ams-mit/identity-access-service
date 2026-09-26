package lk.ac.kelaniya.ams.identity_access_service.controller;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.security.SignatureException;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.RoleResponse;
import lk.ac.kelaniya.ams.identity_access_service.exception.GlobalExceptionHandler;
import lk.ac.kelaniya.ams.identity_access_service.security.JwtService;
import lk.ac.kelaniya.ams.identity_access_service.security.SecurityConfig;
import lk.ac.kelaniya.ams.identity_access_service.service.RoleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoleController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class RoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RoleService roleService;

    @MockBean
    private JwtService jwtService;

    private void mockValidToken(String token, UUID userId, String email, List<String> roles) {
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
    @DisplayName("GET /api/v1/roles returns 401 when unauthenticated (no Bearer token)")
    void testGetAllRoles_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/roles"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/roles returns 401 when Bearer token is invalid")
    void testGetAllRoles_invalidToken_returns401() throws Exception {
        String invalidToken = "invalid.bearer.token";
        given(jwtService.parseAndValidateToken(invalidToken))
                .willThrow(new SignatureException("JWT signature validation failed"));

        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer " + invalidToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/roles returns 401 when Bearer token is expired")
    void testGetAllRoles_expiredToken_returns401() throws Exception {
        String expiredToken = "expired.bearer.token";
        given(jwtService.parseAndValidateToken(expiredToken))
                .willThrow(new ExpiredJwtException(null, null, "JWT expired at 2026-09-12T16:00:00Z"));

        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/roles returns 403 when authenticated user has no roles")
    void testGetAllRoles_authenticatedNoRoles_returns403() throws Exception {
        String token = "valid.token.no.roles";
        UUID userId = UUID.randomUUID();
        mockValidToken(token, userId, "user@example.com", List.of());

        mockMvc.perform(get("/api/v1/roles")
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
    @DisplayName("GET /api/v1/roles returns 403 for all non-admin authenticated roles")
    void testGetAllRoles_authenticatedNonAdminRole_returns403(String role) throws Exception {
        String token = "valid.token." + role.toLowerCase();
        UUID userId = UUID.randomUUID();
        mockValidToken(token, userId, "user." + role.toLowerCase() + "@example.com", List.of(role));

        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")))
                .andExpect(jsonPath("$.error.message", is("Access denied: insufficient permissions")));
    }

    @Test
    @DisplayName("GET /api/v1/roles returns 200 with all 8 roles when authenticated as SYSTEM_ADMINISTRATOR")
    void testGetAllRoles_systemAdministrator_returns200WithAll8Roles() throws Exception {
        String token = "valid.sysadmin.jwt.token";
        UUID adminId = UUID.randomUUID();
        mockValidToken(token, adminId, "sysadmin@ams.lk", List.of("SYSTEM_ADMINISTRATOR"));

        List<RoleResponse> expectedRoles = List.of(
                RoleResponse.builder().id(UUID.randomUUID()).name("APARTMENT_MANAGER").description("Apartment Manager").build(),
                RoleResponse.builder().id(UUID.randomUUID()).name("FINANCE_OFFICER").description("Finance Officer").build(),
                RoleResponse.builder().id(UUID.randomUUID()).name("MAINTENANCE_COORDINATOR").description("Maintenance Coordinator").build(),
                RoleResponse.builder().id(UUID.randomUUID()).name("OWNER").description("Property Owner").build(),
                RoleResponse.builder().id(UUID.randomUUID()).name("SECURITY_OFFICER").description("Security Staff").build(),
                RoleResponse.builder().id(UUID.randomUUID()).name("SYSTEM_ADMINISTRATOR").description("Full administrative access").build(),
                RoleResponse.builder().id(UUID.randomUUID()).name("TECHNICIAN").description("Maintenance Technician").build(),
                RoleResponse.builder().id(UUID.randomUUID()).name("TENANT_RESIDENT").description("Tenant or Resident").build()
        );

        given(roleService.getAllRoles()).willReturn(expectedRoles);

        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(8)))
                .andExpect(jsonPath("$.data[0].name", is("APARTMENT_MANAGER")))
                .andExpect(jsonPath("$.data[1].name", is("FINANCE_OFFICER")))
                .andExpect(jsonPath("$.data[2].name", is("MAINTENANCE_COORDINATOR")))
                .andExpect(jsonPath("$.data[3].name", is("OWNER")))
                .andExpect(jsonPath("$.data[4].name", is("SECURITY_OFFICER")))
                .andExpect(jsonPath("$.data[5].name", is("SYSTEM_ADMINISTRATOR")))
                .andExpect(jsonPath("$.data[6].name", is("TECHNICIAN")))
                .andExpect(jsonPath("$.data[7].name", is("TENANT_RESIDENT")));
    }
}

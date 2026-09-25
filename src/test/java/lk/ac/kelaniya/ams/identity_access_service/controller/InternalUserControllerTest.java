package lk.ac.kelaniya.ams.identity_access_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.security.SignatureException;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.InternalUserResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lk.ac.kelaniya.ams.identity_access_service.exception.GlobalExceptionHandler;
import lk.ac.kelaniya.ams.identity_access_service.exception.UserNotFoundException;
import lk.ac.kelaniya.ams.identity_access_service.security.JwtService;
import lk.ac.kelaniya.ams.identity_access_service.security.SecurityConfig;
import lk.ac.kelaniya.ams.identity_access_service.service.InternalUserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lk.ac.kelaniya.ams.identity_access_service.security.InternalCallerAuthorizationService;

@WebMvcTest(InternalUserController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, InternalCallerAuthorizationService.class})
class InternalUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InternalUserService internalUserService;

    @MockBean
    private JwtService jwtService;

    private void mockValidServiceToken(String token, String serviceName) {
        @SuppressWarnings("unchecked")
        Jws<Claims> claimsJws = (Jws<Claims>) mock(Jws.class);
        Claims claims = mock(Claims.class);
        given(claimsJws.getPayload()).willReturn(claims);
        given(claims.get("type", String.class)).willReturn("service");
        given(claims.getSubject()).willReturn(serviceName);
        given(jwtService.parseAndValidateToken(token)).willReturn(claimsJws);
    }

    private void mockValidUserToken(String token, UUID userId, String email, List<String> roles) {
        @SuppressWarnings("unchecked")
        Jws<Claims> claimsJws = (Jws<Claims>) mock(Jws.class);
        Claims claims = mock(Claims.class);
        given(claimsJws.getPayload()).willReturn(claims);
        given(claims.get("type", String.class)).willReturn("user");
        given(claims.getSubject()).willReturn(userId.toString());
        given(claims.get("roles", List.class)).willReturn(roles);
        given(jwtService.parseAndValidateToken(token)).willReturn(claimsJws);
    }

    @Test
    @DisplayName("GET /internal/v1/users/{userId} with valid service token returns 200 and minimal authorization fields")
    void testGetUserForValidation_validServiceToken_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        String serviceToken = "valid.service.token";

        mockValidServiceToken(serviceToken, "resident-management-service");

        InternalUserResponse response = InternalUserResponse.builder()
                .userId(userId)
                .accountStatus(AccountStatus.ACTIVE)
                .roles(List.of("TENANT_RESIDENT"))
                .build();

        given(internalUserService.getUserForValidation(userId)).willReturn(response);

        mockMvc.perform(get("/internal/v1/users/{userId}", userId)
                        .header("Authorization", "Bearer " + serviceToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(userId.toString())))
                .andExpect(jsonPath("$.accountStatus", is("ACTIVE")))
                .andExpect(jsonPath("$.roles", containsInAnyOrder("TENANT_RESIDENT")))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.firstName").doesNotExist())
                .andExpect(jsonPath("$.lastName").doesNotExist());
    }

    @Test
    @DisplayName("GET /internal/v1/users/{userId} with valid service token from NON-allow-listed service returns 403 FORBIDDEN (Rule 10)")
    void testGetUserForValidation_nonAllowListedServiceToken_returns403Forbidden() throws Exception {
        UUID userId = UUID.randomUUID();
        String serviceToken = "valid.unauthorized.service.token";

        // identity-access-service is a trusted service (ROLE_SERVICE) but not on user-validation allow-list
        mockValidServiceToken(serviceToken, "identity-access-service");

        mockMvc.perform(get("/internal/v1/users/{userId}", userId)
                        .header("Authorization", "Bearer " + serviceToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("GET /internal/v1/users/{userId} with untrusted/unregistered service token returns 401 UNAUTHORIZED with JSON body (Rule 8)")
    void testGetUserForValidation_untrustedServiceToken_returns401Unauthorized() throws Exception {
        UUID userId = UUID.randomUUID();
        String serviceToken = "untrusted.service.token";

        mockValidServiceToken(serviceToken, "unknown-malicious-service");

        mockMvc.perform(get("/internal/v1/users/{userId}", userId)
                        .header("Authorization", "Bearer " + serviceToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")))
                .andExpect(jsonPath("$.error.message", is("Authentication required")));
    }

    @Test
    @DisplayName("GET /internal/v1/users/{userId} with SYSTEM_ADMINISTRATOR user token returns 401 UNAUTHORIZED (token type isolation)")
    void testGetUserForValidation_adminUserToken_returns401Unauthorized() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        String adminToken = "valid.admin.user.token";

        mockValidUserToken(adminToken, adminId, "admin@ams.lk", List.of("SYSTEM_ADMINISTRATOR"));

        mockMvc.perform(get("/internal/v1/users/{userId}", targetUserId)
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")))
                .andExpect(jsonPath("$.error.message", is("Authentication required")));
    }

    @Test
    @DisplayName("GET /internal/v1/users/{userId} with normal resident user token returns 401 UNAUTHORIZED (token type isolation)")
    void testGetUserForValidation_regularUserToken_returns401Unauthorized() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        UUID residentId = UUID.randomUUID();
        String residentToken = "valid.resident.user.token";

        mockValidUserToken(residentToken, residentId, "resident@ams.lk", List.of("TENANT_RESIDENT"));

        mockMvc.perform(get("/internal/v1/users/{userId}", targetUserId)
                        .header("Authorization", "Bearer " + residentToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")))
                .andExpect(jsonPath("$.error.message", is("Authentication required")));
    }

    @Test
    @DisplayName("GET /internal/v1/users/{userId} unauthenticated returns 401 UNAUTHORIZED with standard JSON error body")
    void testGetUserForValidation_unauthenticated_returns401Unauthorized() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/internal/v1/users/{userId}", userId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")))
                .andExpect(jsonPath("$.error.message", is("Authentication required")));
    }

    @Test
    @DisplayName("GET /internal/v1/users/{userId} with invalid token signature returns 401 UNAUTHORIZED")
    void testGetUserForValidation_invalidSignature_returns401Unauthorized() throws Exception {
        UUID userId = UUID.randomUUID();
        String invalidToken = "invalid.signature.token";

        given(jwtService.parseAndValidateToken(invalidToken))
                .willThrow(new SignatureException("Invalid JWT signature"));

        mockMvc.perform(get("/internal/v1/users/{userId}", userId)
                        .header("Authorization", "Bearer " + invalidToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /internal/v1/users/{userId} with valid service token for non-existent user returns 404 NOT FOUND")
    void testGetUserForValidation_userNotFound_returns404NotFound() throws Exception {
        UUID nonExistentUserId = UUID.randomUUID();
        String serviceToken = "valid.service.token";

        mockValidServiceToken(serviceToken, "billing-payment-service");

        given(internalUserService.getUserForValidation(nonExistentUserId))
                .willThrow(new UserNotFoundException("User not found with id: " + nonExistentUserId));

        mockMvc.perform(get("/internal/v1/users/{userId}", nonExistentUserId)
                        .header("Authorization", "Bearer " + serviceToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("USER_NOT_FOUND")));
    }
}

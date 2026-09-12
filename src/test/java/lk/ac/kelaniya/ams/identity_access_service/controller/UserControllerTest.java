package lk.ac.kelaniya.ams.identity_access_service.controller;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.security.SignatureException;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.UserSummaryResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lk.ac.kelaniya.ams.identity_access_service.exception.AccountStatusException;
import lk.ac.kelaniya.ams.identity_access_service.exception.GlobalExceptionHandler;
import lk.ac.kelaniya.ams.identity_access_service.exception.InvalidCredentialsException;
import lk.ac.kelaniya.ams.identity_access_service.security.JwtService;
import lk.ac.kelaniya.ams.identity_access_service.security.SecurityConfig;
import lk.ac.kelaniya.ams.identity_access_service.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

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

    @Test
    @DisplayName("GET /api/v1/users/me returns 401 when unauthenticated (no Bearer token)")
    void testGetCurrentUser_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/users/me returns 401 when Bearer token is invalid")
    void testGetCurrentUser_invalidToken_returns401() throws Exception {
        String invalidToken = "invalid.bearer.token";
        given(jwtService.parseAndValidateToken(invalidToken))
                .willThrow(new SignatureException("JWT signature validation failed"));

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + invalidToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/users/me returns 200 with fresh DB user data reflecting updates")
    void testGetCurrentUser_authenticated_returnsCurrentDbState() throws Exception {
        UUID userId = UUID.randomUUID();
        String validToken = "valid.test.token";

        // Mock token claims with original stale email/role
        mockValidToken(validToken, userId, "stale@example.com", List.of("ROLE_USER"));

        // Mock DB service returning fresh updated data
        UserSummaryResponse freshDbUser = UserSummaryResponse.builder()
                .userId(userId)
                .email("fresh.updated@example.com")
                .firstName("Alice")
                .lastName("Smith")
                .accountStatus(AccountStatus.ACTIVE)
                .roles(List.of("ROLE_MANAGER"))
                .build();

        given(userService.getCurrentUser(userId)).willReturn(freshDbUser);

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(userId.toString())))
                .andExpect(jsonPath("$.email", is("fresh.updated@example.com")))
                .andExpect(jsonPath("$.firstName", is("Alice")))
                .andExpect(jsonPath("$.lastName", is("Smith")))
                .andExpect(jsonPath("$.accountStatus", is("ACTIVE")))
                .andExpect(jsonPath("$.roles[0]", is("ROLE_MANAGER")));
    }

    @Test
    @DisplayName("GET /api/v1/users/me returns 401 when user no longer exists in database")
    void testGetCurrentUser_userNotFound_returns401() throws Exception {
        UUID userId = UUID.randomUUID();
        String validToken = "valid.test.token";

        mockValidToken(validToken, userId, "deleted@example.com", List.of("ROLE_USER"));
        given(userService.getCurrentUser(userId))
                .willThrow(new InvalidCredentialsException("User not found or no longer exists"));

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("INVALID_CREDENTIALS")))
                .andExpect(jsonPath("$.error.message", is("User not found or no longer exists")));
    }

    @Test
    @DisplayName("GET /api/v1/users/me returns 403 when user account is DEACTIVATED in database")
    void testGetCurrentUser_deactivatedAccount_returns403() throws Exception {
        UUID userId = UUID.randomUUID();
        String validToken = "valid.test.token";

        mockValidToken(validToken, userId, "deactivated@example.com", List.of("ROLE_USER"));
        given(userService.getCurrentUser(userId))
                .willThrow(new AccountStatusException("ACCOUNT_DEACTIVATED", "Account has been deactivated. Please contact support."));

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("ACCOUNT_DEACTIVATED")))
                .andExpect(jsonPath("$.error.message", is("Account has been deactivated. Please contact support.")));
    }

    @Test
    @DisplayName("GET /api/v1/users/me returns 401 when Bearer token is expired")
    void testGetCurrentUser_expiredToken_returns401() throws Exception {
        String expiredToken = "expired.bearer.token";
        given(jwtService.parseAndValidateToken(expiredToken))
                .willThrow(new io.jsonwebtoken.ExpiredJwtException(null, null, "JWT expired at 2026-09-12T16:00:00Z"));

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/users/me returns 403 when token was issued while ACTIVE but account deactivated in DB afterward")
    void testGetCurrentUser_tokenIssuedWhileActive_accountDeactivatedInDbAfterward_returns403() throws Exception {
        UUID userId = UUID.randomUUID();
        String validToken = "valid.jwt.issued.while.active";

        mockValidToken(validToken, userId, "active.at.issuance@example.com", List.of("ROLE_RESIDENT"));
        given(userService.getCurrentUser(userId))
                .willThrow(new AccountStatusException("ACCOUNT_DEACTIVATED", "Account has been deactivated. Please contact support."));

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("ACCOUNT_DEACTIVATED")))
                .andExpect(jsonPath("$.error.message", is("Account has been deactivated. Please contact support.")));
    }
}

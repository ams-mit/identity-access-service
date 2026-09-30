package kln.ams.identityaccess.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kln.ams.identityaccess.dto.auth.AuthenticatedUserResponse;
import kln.ams.identityaccess.dto.auth.LoginRequest;
import kln.ams.identityaccess.dto.auth.LoginResponse;
import kln.ams.identityaccess.dto.auth.LoginUserDto;
import kln.ams.identityaccess.exception.AccountInactiveException;
import kln.ams.identityaccess.exception.GlobalExceptionHandler;
import kln.ams.identityaccess.exception.InvalidCredentialsException;
import kln.ams.identityaccess.security.JwtAuthenticationFilter;
import kln.ams.identityaccess.security.JwtService;
import kln.ams.identityaccess.security.RequestIdFilter;
import kln.ams.identityaccess.security.UserPrincipal;
import kln.ams.identityaccess.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private RequestIdFilter requestIdFilter;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    void login_Success_Returns200WithEnvelope() throws Exception {
        LoginRequest request = new LoginRequest("john@example.com", "Password123!");
        LoginUserDto userDto = new LoginUserDto(userId, "john@example.com", List.of("TENANT_RESIDENT"), "ACTIVE");
        LoginResponse response = new LoginResponse("mock_token", "Bearer", 1800, userDto);

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.data.accessToken").value("mock_token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(1800))
                .andExpect(jsonPath("$.data.user.username").value("john@example.com"));
    }

    @Test
    void login_ValidationFailure_Returns400() throws Exception {
        LoginRequest request = new LoginRequest("", "");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void login_InvalidCredentials_Returns401() throws Exception {
        LoginRequest request = new LoginRequest("john@example.com", "WrongPw");
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void login_AccountInactive_Returns403() throws Exception {
        LoginRequest request = new LoginRequest("john@example.com", "Password123!");
        when(authService.login(any())).thenThrow(new AccountInactiveException("Account is inactive"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_INACTIVE"));
    }

    @Test
    void me_Success_Returns200WithEnvelope() throws Exception {
        AuthenticatedUserResponse userResponse = AuthenticatedUserResponse.builder()
                .id(userId)
                .username("john@example.com")
                .email("john@example.com")
                .firstName("John")
                .lastName("Perera")
                .roles(List.of("TENANT_RESIDENT"))
                .status("ACTIVE")
                .build();

        UserPrincipal principal = new UserPrincipal(userId, List.of("TENANT_RESIDENT"));
        when(authService.getCurrentUser(userId)).thenReturn(userResponse);

        mockMvc.perform(get("/api/v1/auth/me")
                        .principal(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(userId.toString()))
                .andExpect(jsonPath("$.data.username").value("john@example.com"))
                .andExpect(jsonPath("$.data.roles[0]").value("TENANT_RESIDENT"));
    }
}

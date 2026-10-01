package kln.ams.identityaccess.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kln.ams.identityaccess.dto.auth.AuthenticatedUserResponse;
import kln.ams.identityaccess.dto.auth.ChangePasswordRequest;
import kln.ams.identityaccess.dto.auth.ForgotPasswordRequest;
import kln.ams.identityaccess.dto.auth.LoginRequest;
import kln.ams.identityaccess.dto.auth.LoginResponse;
import kln.ams.identityaccess.dto.auth.LoginUserDto;
import kln.ams.identityaccess.dto.auth.MessageResponse;
import kln.ams.identityaccess.dto.auth.RegisterRequest;
import kln.ams.identityaccess.dto.auth.RegisterResponse;
import kln.ams.identityaccess.dto.auth.ResetPasswordRequest;
import kln.ams.identityaccess.exception.AccountInactiveException;
import kln.ams.identityaccess.exception.GlobalExceptionHandler;
import kln.ams.identityaccess.exception.InvalidCredentialsException;
import kln.ams.identityaccess.exception.InvalidResetTokenException;
import kln.ams.identityaccess.exception.InvalidTokenException;
import kln.ams.identityaccess.exception.PasswordValidationException;
import kln.ams.identityaccess.exception.UserAlreadyExistsException;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

    // ==========================================
    // AUTH-001: Login
    // ==========================================

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
    void login_ValidationFailure_Returns400WithDetailsList() throws Exception {
        LoginRequest request = new LoginRequest("", "");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details").isArray())
                .andExpect(jsonPath("$.error.details[0].field").exists())
                .andExpect(jsonPath("$.error.details[0].message").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    void authGroupEnvelope_AssertStandardFields() throws Exception {
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
                .andExpect(jsonPath("$.data").isMap())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.requestId").isString());
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
        when(authService.login(any())).thenThrow(new AccountInactiveException("Account is inactive or suspended"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_INACTIVE"));
    }

    // ==========================================
    // AUTH-002: Current User (/me)
    // ==========================================

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
                        .principal(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(userId.toString()))
                .andExpect(jsonPath("$.data.username").value("john@example.com"))
                .andExpect(jsonPath("$.data.roles[0]").value("TENANT_RESIDENT"));
    }

    // ==========================================
    // AUTH-003: Logout
    // ==========================================

    @Test
    void logout_Success_Returns204NoContent() throws Exception {
        UserPrincipal principal = new UserPrincipal(userId, List.of("TENANT_RESIDENT"));
        doNothing().when(authService).logout(userId);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .principal(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void logout_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    // ==========================================
    // AUTH-004: Register
    // ==========================================

    @Test
    void register_Success_Returns201WithEnvelope() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .firstName("John")
                .lastName("Perera")
                .email("john.perera@example.com")
                .phone("+94771234567")
                .password("Password123")
                .confirmPassword("Password123")
                .build();

        RegisterResponse response = RegisterResponse.builder()
                .id(userId)
                .username("john.perera@example.com")
                .email("john.perera@example.com")
                .firstName("John")
                .lastName("Perera")
                .phone("+94771234567")
                .status("INACTIVE")
                .roles(List.of("TENANT_RESIDENT"))
                .createdAt(Instant.now())
                .build();

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User registered successfully"))
                .andExpect(jsonPath("$.data.id").value(userId.toString()))
                .andExpect(jsonPath("$.data.username").value("john.perera@example.com"))
                .andExpect(jsonPath("$.data.status").value("INACTIVE"))
                .andExpect(jsonPath("$.data.roles[0]").value("TENANT_RESIDENT"))
                .andExpect(content().string(not(containsString("Password123"))));
    }

    @Test
    void register_DuplicateEmail_Returns409() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .firstName("John")
                .lastName("Perera")
                .email("existing@example.com")
                .password("Password123")
                .confirmPassword("Password123")
                .build();

        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new UserAlreadyExistsException("User already exists with this email or username"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_ALREADY_EXISTS"));
    }

    @Test
    void register_PasswordMismatch_Returns400() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .firstName("John")
                .lastName("Perera")
                .email("john@example.com")
                .password("Password123")
                .confirmPassword("Different456")
                .build();

        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new PasswordValidationException("Passwords do not match"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void register_WeakPassword_Returns400() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .firstName("John")
                .lastName("Perera")
                .email("john@example.com")
                .password("short")
                .confirmPassword("short")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ==========================================
    // AUTH-005: Forgot Password
    // ==========================================

    @Test
    void forgotPassword_Success_Returns200WithGenericMessage() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest("john@example.com");
        MessageResponse response = new MessageResponse(AuthService.FORGOT_PASSWORD_GENERIC_MESSAGE);

        when(authService.forgotPassword(any(ForgotPasswordRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(AuthService.FORGOT_PASSWORD_GENERIC_MESSAGE))
                .andExpect(jsonPath("$.data.message").value(AuthService.FORGOT_PASSWORD_GENERIC_MESSAGE));
    }

    @Test
    void forgotPassword_UnknownEmail_ReturnsSame200GenericMessage() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest("unknown@example.com");
        MessageResponse response = new MessageResponse(AuthService.FORGOT_PASSWORD_GENERIC_MESSAGE);

        when(authService.forgotPassword(any(ForgotPasswordRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(AuthService.FORGOT_PASSWORD_GENERIC_MESSAGE));
    }

    @Test
    void forgotPassword_InvalidEmail_Returns400() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest("not-an-email");

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ==========================================
    // AUTH-006: Reset Password
    // ==========================================

    @Test
    void resetPassword_Success_Returns200WithEnvelope() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest("token-123", "NewPassword123", "NewPassword123");
        MessageResponse response = new MessageResponse(AuthService.RESET_PASSWORD_SUCCESS_MESSAGE);

        when(authService.resetPassword(any(ResetPasswordRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(AuthService.RESET_PASSWORD_SUCCESS_MESSAGE));
    }

    @Test
    void resetPassword_InvalidOrExpiredToken_Returns400GenericError() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest("expired-token", "NewPassword123", "NewPassword123");

        when(authService.resetPassword(any(ResetPasswordRequest.class)))
                .thenThrow(new InvalidResetTokenException());

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_RESET_TOKEN"))
                .andExpect(jsonPath("$.message").value(InvalidResetTokenException.DEFAULT_MESSAGE));
    }

    // ==========================================
    // AUTH-007: Change Password
    // ==========================================

    @Test
    void changePassword_Success_Returns204NoContent() throws Exception {
        UserPrincipal principal = new UserPrincipal(userId, List.of("TENANT_RESIDENT"));
        ChangePasswordRequest request = new ChangePasswordRequest("OldPassword123", "NewPassword456", "NewPassword456");

        doNothing().when(authService).changePassword(eq(userId), any(ChangePasswordRequest.class));

        mockMvc.perform(put("/api/v1/auth/me/password")
                        .principal(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void changePassword_WrongCurrentPassword_Returns400InvalidCredentials() throws Exception {
        UserPrincipal principal = new UserPrincipal(userId, List.of("TENANT_RESIDENT"));
        ChangePasswordRequest request = new ChangePasswordRequest("WrongPassword123", "NewPassword456", "NewPassword456");

        doThrow(new InvalidCredentialsException("Current password is incorrect", HttpStatus.BAD_REQUEST))
                .when(authService).changePassword(eq(userId), any(ChangePasswordRequest.class));

        mockMvc.perform(put("/api/v1/auth/me/password")
                        .principal(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void changePassword_SamePassword_Returns400ValidationError() throws Exception {
        UserPrincipal principal = new UserPrincipal(userId, List.of("TENANT_RESIDENT"));
        ChangePasswordRequest request = new ChangePasswordRequest("SamePassword123", "SamePassword123", "SamePassword123");

        doThrow(new PasswordValidationException("New password must differ from current password"))
                .when(authService).changePassword(eq(userId), any(ChangePasswordRequest.class));

        mockMvc.perform(put("/api/v1/auth/me/password")
                        .principal(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void changePassword_Unauthenticated_Returns401() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("OldPassword123", "NewPassword456", "NewPassword456");

        mockMvc.perform(put("/api/v1/auth/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    @Test
    void logout_ServicePrincipal_Returns401() throws Exception {
        kln.ams.identityaccess.security.ServicePrincipal servicePrincipal =
                new kln.ams.identityaccess.security.ServicePrincipal("resident-management-service");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .principal(new UsernamePasswordAuthenticationToken(servicePrincipal, null, servicePrincipal.getAuthorities())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    @Test
    void changePassword_ServicePrincipal_Returns401() throws Exception {
        kln.ams.identityaccess.security.ServicePrincipal servicePrincipal =
                new kln.ams.identityaccess.security.ServicePrincipal("resident-management-service");
        ChangePasswordRequest request = new ChangePasswordRequest("OldPassword123", "NewPassword456", "NewPassword456");

        mockMvc.perform(put("/api/v1/auth/me/password")
                        .principal(new UsernamePasswordAuthenticationToken(servicePrincipal, null, servicePrincipal.getAuthorities()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }
}

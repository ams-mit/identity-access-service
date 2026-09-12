package lk.ac.kelaniya.ams.identity_access_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.ac.kelaniya.ams.identity_access_service.dto.request.LoginRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.request.RegisterRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.LoginResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.RegisterResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lk.ac.kelaniya.ams.identity_access_service.exception.AccountLockedException;
import lk.ac.kelaniya.ams.identity_access_service.exception.AccountStatusException;
import lk.ac.kelaniya.ams.identity_access_service.exception.DuplicateEmailException;
import lk.ac.kelaniya.ams.identity_access_service.exception.GlobalExceptionHandler;
import lk.ac.kelaniya.ams.identity_access_service.exception.InvalidCredentialsException;
import lk.ac.kelaniya.ams.identity_access_service.exception.PasswordMismatchException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.security.SignatureException;
import lk.ac.kelaniya.ams.identity_access_service.security.JwtAuthenticationFilter;
import lk.ac.kelaniya.ams.identity_access_service.security.JwtService;
import lk.ac.kelaniya.ams.identity_access_service.security.SecurityConfig;
import lk.ac.kelaniya.ams.identity_access_service.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtService jwtService;

    @Test
    @DisplayName("POST /api/v1/auth/register returns 201 with RegisterResponse on valid payload")
    void testRegister_success() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .firstName("Jane")
                .lastName("Doe")
                .email("jane.doe@example.com")
                .phone("+94771234567")
                .password("SecurePass1")
                .confirmPassword("SecurePass1")
                .build();

        UUID generatedId = UUID.randomUUID();
        RegisterResponse expectedResponse = RegisterResponse.builder()
                .userId(generatedId)
                .email("jane.doe@example.com")
                .accountStatus(AccountStatus.PENDING_VERIFICATION)
                .build();

        given(authService.register(any(RegisterRequest.class))).willReturn(expectedResponse);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId", is(generatedId.toString())))
                .andExpect(jsonPath("$.email", is("jane.doe@example.com")))
                .andExpect(jsonPath("$.accountStatus", is("PENDING_VERIFICATION")))
                .andExpect(content().string(not(containsString("SecurePass1"))));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register returns 400 when password does not match confirmPassword")
    void testRegister_passwordMismatch() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .firstName("Jane")
                .lastName("Doe")
                .email("jane.doe@example.com")
                .password("SecurePass1")
                .confirmPassword("DifferentPass2")
                .build();

        given(authService.register(any(RegisterRequest.class)))
                .willThrow(new PasswordMismatchException("Passwords do not match"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("PASSWORD_MISMATCH")))
                .andExpect(jsonPath("$.error.message", is("Passwords do not match")))
                .andExpect(content().string(not(containsString("SecurePass1"))))
                .andExpect(content().string(not(containsString("DifferentPass2"))));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register returns 409 when email already exists")
    void testRegister_duplicateEmail() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .firstName("Jane")
                .lastName("Doe")
                .email("existing@example.com")
                .password("SecurePass1")
                .confirmPassword("SecurePass1")
                .build();

        given(authService.register(any(RegisterRequest.class)))
                .willThrow(new DuplicateEmailException("Email already in use"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("EMAIL_ALREADY_EXISTS")))
                .andExpect(jsonPath("$.error.message", is("Email already in use")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register returns 400 on invalid email format")
    void testRegister_invalidEmail() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .firstName("Jane")
                .lastName("Doe")
                .email("invalid-email-format")
                .password("SecurePass1")
                .confirmPassword("SecurePass1")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.error.message", containsString("Email must be a valid email address")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register returns 400 when password is under minimum length")
    void testRegister_shortPassword() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .firstName("Jane")
                .lastName("Doe")
                .email("jane.doe@example.com")
                .password("Pass1")
                .confirmPassword("Pass1")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register returns 400 when password has no digits")
    void testRegister_passwordNoDigit() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .firstName("Jane")
                .lastName("Doe")
                .email("jane.doe@example.com")
                .password("PasswordNoDigits")
                .confirmPassword("PasswordNoDigits")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.error.message", containsString("numeric digit")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register returns 400 when required fields are missing")
    void testRegister_missingFields() throws Exception {
        RegisterRequest request = RegisterRequest.builder().build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login returns 200 with LoginResponse on valid credentials")
    void testLogin_success() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("john.doe@example.com")
                .password("SecretPass123")
                .build();

        UUID userId = UUID.randomUUID();
        LoginResponse response = LoginResponse.builder()
                .accessToken("mocked.rs256.jwt.token")
                .expiresIn(1800L)
                .user(LoginResponse.UserSummary.builder()
                        .userId(userId)
                        .email("john.doe@example.com")
                        .roles(List.of("RESIDENT"))
                        .build())
                .build();

        given(authService.login(any(LoginRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", is("mocked.rs256.jwt.token")))
                .andExpect(jsonPath("$.expiresIn", is(1800)))
                .andExpect(jsonPath("$.user.userId", is(userId.toString())))
                .andExpect(jsonPath("$.user.email", is("john.doe@example.com")))
                .andExpect(jsonPath("$.user.roles[0]", is("RESIDENT")))
                .andExpect(content().string(not(containsString("SecretPass123"))));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login returns generic 401 on invalid credentials")
    void testLogin_invalidCredentials_returns401() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("unknown@example.com")
                .password("WrongPassword123")
                .build();

        given(authService.login(any(LoginRequest.class)))
                .willThrow(new InvalidCredentialsException("Invalid email or password."));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("INVALID_CREDENTIALS")))
                .andExpect(jsonPath("$.error.message", is("Invalid email or password.")))
                .andExpect(content().string(not(containsString("WrongPassword123"))));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login returns 403 on PENDING_VERIFICATION account")
    void testLogin_pendingVerification_returns403() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("pending@example.com")
                .password("CorrectPassword123")
                .build();

        given(authService.login(any(LoginRequest.class)))
                .willThrow(new AccountStatusException("ACCOUNT_PENDING_VERIFICATION", "Account is pending verification. Please verify your email before logging in."));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("ACCOUNT_PENDING_VERIFICATION")))
                .andExpect(jsonPath("$.error.message", containsString("pending verification")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login returns 403 on SUSPENDED account")
    void testLogin_suspended_returns403() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("suspended@example.com")
                .password("CorrectPassword123")
                .build();

        given(authService.login(any(LoginRequest.class)))
                .willThrow(new AccountStatusException("ACCOUNT_SUSPENDED", "Account has been suspended. Please contact support."));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("ACCOUNT_SUSPENDED")))
                .andExpect(jsonPath("$.error.message", containsString("suspended")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login returns 403 on DEACTIVATED account")
    void testLogin_deactivated_returns403() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("deactivated@example.com")
                .password("CorrectPassword123")
                .build();

        given(authService.login(any(LoginRequest.class)))
                .willThrow(new AccountStatusException("ACCOUNT_DEACTIVATED", "Account has been deactivated. Please contact support."));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("ACCOUNT_DEACTIVATED")))
                .andExpect(jsonPath("$.error.message", containsString("deactivated")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login returns 400 on invalid email format")
    void testLogin_invalidEmail_returns400() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("invalid-email-format")
                .password("CorrectPassword123")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.error.message", containsString("Email must be a valid email address")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login returns 400 when password is blank")
    void testLogin_blankPassword_returns400() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("valid@example.com")
                .password("")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.error.message", containsString("Password is required")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login returns 423 when account is locked")
    void testLogin_accountLocked_returns423() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("locked@example.com")
                .password("AnyPassword123")
                .build();

        Instant lockoutExpiry = Instant.now().plus(Duration.ofMinutes(15));
        given(authService.login(any(LoginRequest.class)))
                .willThrow(new AccountLockedException("Account is temporarily locked. Try again after " + lockoutExpiry + ".", lockoutExpiry));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error.code", is("ACCOUNT_LOCKED")))
                .andExpect(jsonPath("$.error.message", containsString("Account is temporarily locked")))
                .andExpect(jsonPath("$.error.message", containsString(lockoutExpiry.toString())));
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout returns 401 when unauthenticated (no Bearer token)")
    void testLogout_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout returns 204 No Content with valid Bearer token")
    void testLogout_authenticated_returns204() throws Exception {
        String validToken = "valid.rs256.jwt.token";

        @SuppressWarnings("unchecked")
        Jws<Claims> claimsJws = (Jws<Claims>) mock(Jws.class);
        Claims claims = mock(Claims.class);
        given(claimsJws.getPayload()).willReturn(claims);
        given(claims.getSubject()).willReturn(UUID.randomUUID().toString());
        given(claims.get("roles", List.class)).willReturn(List.of("RESIDENT"));
        given(jwtService.parseAndValidateToken(validToken)).willReturn(claimsJws);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout returns 401 when Bearer token is invalid")
    void testLogout_invalidToken_returns401() throws Exception {
        String invalidToken = "invalid.tampered.token";
        given(jwtService.parseAndValidateToken(invalidToken))
                .willThrow(new SignatureException("JWT signature does not match"));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + invalidToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/auth/register returns 409 when DataIntegrityViolationException occurs on concurrent duplicate")
    void testRegister_concurrencyDataIntegrityViolation_returns409() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .firstName("Jane")
                .lastName("Doe")
                .email("jane.doe@example.com")
                .phone("+94771234567")
                .password("SecurePass1")
                .confirmPassword("SecurePass1")
                .build();

        given(authService.register(any(RegisterRequest.class)))
                .willThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicate entry for key 'users.email'"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("EMAIL_ALREADY_EXISTS")))
                .andExpect(jsonPath("$.error.message", containsString("Email is already registered")));
    }
}

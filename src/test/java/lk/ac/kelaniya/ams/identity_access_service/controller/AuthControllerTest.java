package lk.ac.kelaniya.ams.identity_access_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.ac.kelaniya.ams.identity_access_service.dto.request.RegisterRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.RegisterResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lk.ac.kelaniya.ams.identity_access_service.exception.DuplicateEmailException;
import lk.ac.kelaniya.ams.identity_access_service.exception.GlobalExceptionHandler;
import lk.ac.kelaniya.ams.identity_access_service.exception.PasswordMismatchException;
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

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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
}

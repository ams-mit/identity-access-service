package kln.ams.identityaccess.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kln.ams.identityaccess.dto.internal.UserStatusResponse;
import kln.ams.identityaccess.dto.internal.UserValidationResponse;
import kln.ams.identityaccess.exception.GlobalExceptionHandler;
import kln.ams.identityaccess.exception.UserNotFoundException;
import kln.ams.identityaccess.security.InternalCallerAuthorizationService;
import kln.ams.identityaccess.security.JwtAuthenticationFilter;
import kln.ams.identityaccess.security.JwtService;
import kln.ams.identityaccess.security.RequestIdFilter;
import kln.ams.identityaccess.security.ServicePrincipal;
import kln.ams.identityaccess.service.InternalUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalUserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class InternalUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InternalUserService internalUserService;

    @MockBean
    private InternalCallerAuthorizationService callerAuthorizationService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private RequestIdFilter requestIdFilter;

    private UUID userId;
    private ServicePrincipal allowedService;
    private ServicePrincipal unauthorizedService;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        allowedService = new ServicePrincipal("resident-management-service");
        unauthorizedService = new ServicePrincipal("untrusted-service");
    }

    @Test
    void validateUser_Success() throws Exception {
        UserValidationResponse response = UserValidationResponse.builder()
                .userId(userId)
                .exists(true)
                .active(true)
                .roles(List.of("TENANT_RESIDENT"))
                .build();

        when(callerAuthorizationService.isAllowed("resident-management-service", InternalCallerAuthorizationService.USER_VALIDATION_ENDPOINT))
                .thenReturn(true);
        when(internalUserService.validateUser(userId, null)).thenReturn(response);

        mockMvc.perform(get("/api/v1/internal/users/" + userId + "/validate")
                        .principal(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(allowedService, null, allowedService.getAuthorities())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.exists").value(true))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.roles[0]").value("TENANT_RESIDENT"));
    }

    @Test
    void validateUser_UnauthorizedCaller_Returns403() throws Exception {
        when(callerAuthorizationService.isAllowed("untrusted-service", InternalCallerAuthorizationService.USER_VALIDATION_ENDPOINT))
                .thenReturn(false);

        mockMvc.perform(get("/api/v1/internal/users/" + userId + "/validate")
                        .principal(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(unauthorizedService, null, unauthorizedService.getAuthorities())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CALLER_SERVICE_NOT_ALLOWED"));
    }

    @Test
    void validateUser_NotFound_Returns404() throws Exception {
        when(callerAuthorizationService.isAllowed("resident-management-service", InternalCallerAuthorizationService.USER_VALIDATION_ENDPOINT))
                .thenReturn(true);
        when(internalUserService.validateUser(userId, null)).thenThrow(new UserNotFoundException("User not found"));

        mockMvc.perform(get("/api/v1/internal/users/" + userId + "/validate")
                        .principal(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(allowedService, null, allowedService.getAuthorities())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    @Test
    void getUserStatus_Success() throws Exception {
        UserStatusResponse response = UserStatusResponse.builder()
                .userId(userId)
                .status("ACTIVE")
                .active(true)
                .build();

        when(callerAuthorizationService.isAllowed("resident-management-service", InternalCallerAuthorizationService.USER_STATUS_ENDPOINT))
                .thenReturn(true);
        when(internalUserService.getUserStatus(userId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/internal/users/" + userId + "/status")
                        .principal(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(allowedService, null, allowedService.getAuthorities())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.active").value(true));
    }
}

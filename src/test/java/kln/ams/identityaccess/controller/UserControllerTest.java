package kln.ams.identityaccess.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kln.ams.identityaccess.dto.response.PagedData;
import kln.ams.identityaccess.dto.user.CreateUserRequest;
import kln.ams.identityaccess.dto.user.ReplaceUserRolesRequest;
import kln.ams.identityaccess.dto.user.UpdateStatusRequest;
import kln.ams.identityaccess.dto.user.UpdateUserRequest;
import kln.ams.identityaccess.dto.user.UserResponse;
import kln.ams.identityaccess.dto.user.UserRolesResponse;
import kln.ams.identityaccess.entity.AccountStatus;
import kln.ams.identityaccess.exception.DuplicateResourceException;
import kln.ams.identityaccess.exception.GlobalExceptionHandler;
import kln.ams.identityaccess.exception.InvalidRoleException;
import kln.ams.identityaccess.exception.UserNotFoundException;
import kln.ams.identityaccess.security.JwtAuthenticationFilter;
import kln.ams.identityaccess.security.JwtService;
import kln.ams.identityaccess.security.RequestIdFilter;
import kln.ams.identityaccess.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private RequestIdFilter requestIdFilter;

    private UUID userId;
    private UserResponse testUserResponse;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUserResponse = UserResponse.builder()
                .id(userId)
                .username("john@example.com")
                .email("john@example.com")
                .firstName("John")
                .lastName("Perera")
                .phone("0771234567")
                .status("ACTIVE")
                .roles(List.of("TENANT_RESIDENT"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void listUsers_Success() throws Exception {
        PagedData<UserResponse> pagedData = PagedData.<UserResponse>builder()
                .items(List.of(testUserResponse))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .build();

        when(userService.getUsers(anyInt(), anyInt(), any(), any(), any())).thenReturn(pagedData);

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].username").value("john@example.com"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void createUser_Success_Returns201() throws Exception {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("new@example.com")
                .email("new@example.com")
                .password("Password123!")
                .firstName("John")
                .lastName("Perera")
                .roles(List.of("TENANT_RESIDENT"))
                .build();

        when(userService.createUser(any(CreateUserRequest.class))).thenReturn(testUserResponse);

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(userId.toString()));
    }

    @Test
    void createUser_Conflict_Returns409() throws Exception {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("existing@example.com")
                .email("existing@example.com")
                .password("Password123!")
                .firstName("John")
                .lastName("Perera")
                .roles(List.of("TENANT_RESIDENT"))
                .build();

        when(userService.createUser(any())).thenThrow(new DuplicateResourceException("USER_ALREADY_EXISTS", "User already exists"));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_ALREADY_EXISTS"));
    }

    @Test
    void createUser_InvalidRole_Returns422() throws Exception {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("new@example.com")
                .email("new@example.com")
                .password("Password123!")
                .firstName("John")
                .lastName("Perera")
                .roles(List.of("NON_CANONICAL"))
                .build();

        when(userService.createUser(any())).thenThrow(new InvalidRoleException("Invalid canonical role"));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_ROLE"));
    }

    @Test
    void getUser_Success() throws Exception {
        when(userService.getUser(userId)).thenReturn(testUserResponse);

        mockMvc.perform(get("/api/v1/users/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(userId.toString()));
    }

    @Test
    void getUser_NotFound_Returns404() throws Exception {
        when(userService.getUser(userId)).thenThrow(new UserNotFoundException("User not found"));

        mockMvc.perform(get("/api/v1/users/" + userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    @Test
    void updateUser_Success() throws Exception {
        UpdateUserRequest request = UpdateUserRequest.builder()
                .firstName("Johnny")
                .build();

        when(userService.updateUser(eq(userId), any())).thenReturn(testUserResponse);

        mockMvc.perform(patch("/api/v1/users/" + userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void updateStatus_Success() throws Exception {
        UpdateStatusRequest request = new UpdateStatusRequest(AccountStatus.SUSPENDED);
        when(userService.updateStatus(eq(userId), any())).thenReturn(testUserResponse);

        mockMvc.perform(patch("/api/v1/users/" + userId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getUserRoles_Success() throws Exception {
        UserRolesResponse response = new UserRolesResponse(userId, List.of("TENANT_RESIDENT"));
        when(userService.getUserRoles(userId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/users/" + userId + "/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.roles[0]").value("TENANT_RESIDENT"));
    }

    @Test
    void replaceUserRoles_Success() throws Exception {
        ReplaceUserRolesRequest request = new ReplaceUserRolesRequest(List.of("OWNER"));
        UserRolesResponse response = new UserRolesResponse(userId, List.of("OWNER"));
        when(userService.replaceUserRoles(eq(userId), any())).thenReturn(response);

        mockMvc.perform(put("/api/v1/users/" + userId + "/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.roles[0]").value("OWNER"));
    }
}

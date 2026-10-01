package kln.ams.identityaccess.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kln.ams.identityaccess.dto.permission.PermissionResponse;
import kln.ams.identityaccess.dto.permission.ReplaceRolePermissionsRequest;
import kln.ams.identityaccess.dto.permission.RolePermissionsResponse;
import kln.ams.identityaccess.exception.GlobalExceptionHandler;
import kln.ams.identityaccess.exception.PermissionNotFoundException;
import kln.ams.identityaccess.exception.RoleNotFoundException;
import kln.ams.identityaccess.security.JwtAuthenticationFilter;
import kln.ams.identityaccess.security.JwtService;
import kln.ams.identityaccess.security.RequestIdFilter;
import kln.ams.identityaccess.service.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PermissionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PermissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PermissionService permissionService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private RequestIdFilter requestIdFilter;

    private UUID permId;
    private UUID roleId;
    private PermissionResponse testPermResponse;

    @BeforeEach
    void setUp() {
        permId = UUID.randomUUID();
        roleId = UUID.randomUUID();
        testPermResponse = new PermissionResponse(permId, "USER_MANAGE", "Manage users", true);
    }

    @Test
    void listPermissions_Success() throws Exception {
        when(permissionService.getPermissions()).thenReturn(List.of(testPermResponse));

        mockMvc.perform(get("/api/v1/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].code").value("USER_MANAGE"));
    }

    @Test
    void getRolePermissions_Success() throws Exception {
        RolePermissionsResponse response = new RolePermissionsResponse(roleId, List.of(testPermResponse));
        when(permissionService.getRolePermissions(roleId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/roles/" + roleId + "/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.roleId").value(roleId.toString()))
                .andExpect(jsonPath("$.data.permissions[0].code").value("USER_MANAGE"));
    }

    @Test
    void getRolePermissions_RoleNotFound_Returns404() throws Exception {
        when(permissionService.getRolePermissions(roleId)).thenThrow(new RoleNotFoundException("Role not found"));

        mockMvc.perform(get("/api/v1/roles/" + roleId + "/permissions"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ROLE_NOT_FOUND"));
    }

    @Test
    void replaceRolePermissions_Success() throws Exception {
        ReplaceRolePermissionsRequest request = new ReplaceRolePermissionsRequest(List.of(permId));
        RolePermissionsResponse response = new RolePermissionsResponse(roleId, List.of(testPermResponse));
        when(permissionService.replaceRolePermissions(eq(roleId), any())).thenReturn(response);

        mockMvc.perform(put("/api/v1/roles/" + roleId + "/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.permissions[0].code").value("USER_MANAGE"));
    }

    @Test
    void replaceRolePermissions_PermissionNotFound_Returns422() throws Exception {
        ReplaceRolePermissionsRequest request = new ReplaceRolePermissionsRequest(List.of(permId));
        when(permissionService.replaceRolePermissions(eq(roleId), any()))
                .thenThrow(new PermissionNotFoundException("Permission not found with ID: " + permId));

        mockMvc.perform(put("/api/v1/roles/" + roleId + "/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PERMISSION_NOT_FOUND"));
    }
}

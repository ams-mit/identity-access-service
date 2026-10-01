package kln.ams.identityaccess.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kln.ams.identityaccess.dto.role.CreateRoleRequest;
import kln.ams.identityaccess.dto.role.RoleResponse;
import kln.ams.identityaccess.dto.role.UpdateRoleRequest;
import kln.ams.identityaccess.exception.DuplicateResourceException;
import kln.ams.identityaccess.exception.GlobalExceptionHandler;
import kln.ams.identityaccess.exception.RoleInUseException;
import kln.ams.identityaccess.exception.RoleNotFoundException;
import kln.ams.identityaccess.security.JwtAuthenticationFilter;
import kln.ams.identityaccess.security.JwtService;
import kln.ams.identityaccess.security.RequestIdFilter;
import kln.ams.identityaccess.service.RoleService;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoleController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class RoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RoleService roleService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private RequestIdFilter requestIdFilter;

    private UUID roleId;
    private RoleResponse testRoleResponse;

    @BeforeEach
    void setUp() {
        roleId = UUID.randomUUID();
        testRoleResponse = new RoleResponse(roleId, "TECHNICIAN", "Maintenance technician", true);
    }

    @Test
    void listRoles_Success() throws Exception {
        when(roleService.getRoles()).thenReturn(List.of(testRoleResponse));

        mockMvc.perform(get("/api/v1/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Roles retrieved successfully"))
                .andExpect(jsonPath("$.data[0].name").value("TECHNICIAN"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    void listRoles_ReturnsAllNineCanonicalRoles() throws Exception {
        List<String> canonicalNames = List.of(
                "SYSTEM_ADMINISTRATOR", "APARTMENT_MANAGER", "OWNER", "TENANT_RESIDENT",
                "FINANCE_OFFICER", "MAINTENANCE_COORDINATOR", "TECHNICIAN", "SERVICE_STAFF", "SECURITY_OFFICER"
        );
        List<RoleResponse> nineRoles = canonicalNames.stream()
                .map(name -> new RoleResponse(UUID.randomUUID(), name, name + " description", true))
                .toList();

        when(roleService.getRoles()).thenReturn(nineRoles);

        mockMvc.perform(get("/api/v1/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(9))
                .andExpect(jsonPath("$.data[0].name").value("SYSTEM_ADMINISTRATOR"))
                .andExpect(jsonPath("$.data[1].name").value("APARTMENT_MANAGER"))
                .andExpect(jsonPath("$.data[2].name").value("OWNER"))
                .andExpect(jsonPath("$.data[3].name").value("TENANT_RESIDENT"))
                .andExpect(jsonPath("$.data[4].name").value("FINANCE_OFFICER"))
                .andExpect(jsonPath("$.data[5].name").value("MAINTENANCE_COORDINATOR"))
                .andExpect(jsonPath("$.data[6].name").value("TECHNICIAN"))
                .andExpect(jsonPath("$.data[7].name").value("SERVICE_STAFF"))
                .andExpect(jsonPath("$.data[8].name").value("SECURITY_OFFICER"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    void createRole_Success_Returns201() throws Exception {
        CreateRoleRequest request = new CreateRoleRequest("TECHNICIAN", "Maintenance technician");
        when(roleService.createRole(any())).thenReturn(testRoleResponse);

        mockMvc.perform(post("/api/v1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("TECHNICIAN"));
    }

    @Test
    void createRole_Conflict_Returns409() throws Exception {
        CreateRoleRequest request = new CreateRoleRequest("TECHNICIAN", "Maintenance technician");
        when(roleService.createRole(any())).thenThrow(new DuplicateResourceException("ROLE_ALREADY_EXISTS", "Role already exists"));

        mockMvc.perform(post("/api/v1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ROLE_ALREADY_EXISTS"));
    }

    @Test
    void getRole_Success() throws Exception {
        when(roleService.getRole(roleId)).thenReturn(testRoleResponse);

        mockMvc.perform(get("/api/v1/roles/" + roleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Role retrieved successfully"))
                .andExpect(jsonPath("$.data.id").value(roleId.toString()))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.requestId").isString());
    }

    @Test
    void roleGroupEnvelope_AssertStandardFields() throws Exception {
        when(roleService.getRole(roleId)).thenReturn(testRoleResponse);

        mockMvc.perform(get("/api/v1/roles/" + roleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").isMap())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.requestId").isString());
    }

    @Test
    void updateRole_Success() throws Exception {
        UpdateRoleRequest request = new UpdateRoleRequest("Updated description");
        when(roleService.updateRole(eq(roleId), any())).thenReturn(testRoleResponse);

        mockMvc.perform(patch("/api/v1/roles/" + roleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void updateRole_PutMethodNotAllowed_Returns405() throws Exception {
        UpdateRoleRequest request = new UpdateRoleRequest("Updated description");

        mockMvc.perform(put("/api/v1/roles/" + roleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void updateRole_CanonicalRoleConflict_Returns409() throws Exception {
        UpdateRoleRequest request = UpdateRoleRequest.builder()
                .name("NEW_NAME")
                .description("Updated description")
                .build();
        when(roleService.updateRole(eq(roleId), any()))
                .thenThrow(new kln.ams.identityaccess.exception.RoleConflictException("Cannot change name of canonical role: SYSTEM_ADMINISTRATOR"));

        mockMvc.perform(patch("/api/v1/roles/" + roleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ROLE_CONFLICT"));
    }

    @Test
    void deleteRole_Success_Returns204() throws Exception {
        doNothing().when(roleService).deleteRole(roleId);

        mockMvc.perform(delete("/api/v1/roles/" + roleId))
                .andExpect(status().isNoContent())
                .andExpect(jsonPath("$").doesNotExist());
    }

    @Test
    void deleteRole_InUse_Returns409() throws Exception {
        doThrow(new RoleInUseException("Role cannot be deleted while referenced by active user assignments"))
                .when(roleService).deleteRole(roleId);

        mockMvc.perform(delete("/api/v1/roles/" + roleId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ROLE_IN_USE"));
    }
}

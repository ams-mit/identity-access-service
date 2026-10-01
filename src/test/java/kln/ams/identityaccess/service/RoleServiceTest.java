package kln.ams.identityaccess.service;

import kln.ams.identityaccess.dto.role.CreateRoleRequest;
import kln.ams.identityaccess.dto.role.RoleResponse;
import kln.ams.identityaccess.dto.role.UpdateRoleRequest;
import kln.ams.identityaccess.entity.Role;
import kln.ams.identityaccess.exception.DuplicateResourceException;
import kln.ams.identityaccess.exception.RoleInUseException;
import kln.ams.identityaccess.exception.RoleNotFoundException;
import kln.ams.identityaccess.repository.RoleRepository;
import kln.ams.identityaccess.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @InjectMocks
    private RoleService roleService;

    private Role testRole;
    private UUID roleId;

    @BeforeEach
    void setUp() {
        roleId = UUID.randomUUID();
        testRole = Role.builder()
                .id(roleId)
                .name("SERVICE_STAFF")
                .description("Service staff role")
                .active(true)
                .build();
    }

    @Test
    void getRoles_ReturnsList() {
        when(roleRepository.findAll()).thenReturn(List.of(testRole));

        List<RoleResponse> result = roleService.getRoles();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("SERVICE_STAFF");
    }

    @Test
    void createRole_Success() {
        CreateRoleRequest request = new CreateRoleRequest("TECHNICIAN", "Maintenance Technician");

        when(roleRepository.existsByNameIgnoreCase("TECHNICIAN")).thenReturn(false);
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> {
            Role r = invocation.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        RoleResponse response = roleService.createRole(request);

        assertThat(response.getName()).isEqualTo("TECHNICIAN");
        assertThat(response.getDescription()).isEqualTo("Maintenance Technician");
    }

    @Test
    void createRole_Duplicate_ThrowsException() {
        CreateRoleRequest request = new CreateRoleRequest("SERVICE_STAFF", "Duplicate");
        when(roleRepository.existsByNameIgnoreCase("SERVICE_STAFF")).thenReturn(true);

        assertThatThrownBy(() -> roleService.createRole(request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void getRole_Success() {
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(testRole));

        RoleResponse response = roleService.getRole(roleId);

        assertThat(response.getId()).isEqualTo(roleId);
        assertThat(response.getName()).isEqualTo("SERVICE_STAFF");
    }

    @Test
    void updateRole_Success() {
        UpdateRoleRequest request = new UpdateRoleRequest("Updated description");
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(testRole));
        when(roleRepository.save(any(Role.class))).thenReturn(testRole);

        RoleResponse response = roleService.updateRole(roleId, request);

        assertThat(response.getDescription()).isEqualTo("Updated description");
    }

    @Test
    void updateRole_CanonicalRoleNameChange_ThrowsRoleConflictException() {
        UpdateRoleRequest request = UpdateRoleRequest.builder()
                .name("NEW_CUSTOM_ROLE")
                .description("Updated description")
                .build();
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(testRole));

        assertThatThrownBy(() -> roleService.updateRole(roleId, request))
                .isInstanceOf(kln.ams.identityaccess.exception.RoleConflictException.class)
                .hasMessageContaining("Cannot change name of canonical role: SERVICE_STAFF");
    }

    @Test
    void updateRole_SameCanonicalRoleName_UpdatesDescriptionSuccessfully() {
        UpdateRoleRequest request = UpdateRoleRequest.builder()
                .name("SERVICE_STAFF")
                .description("New updated description")
                .build();
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(testRole));
        when(roleRepository.save(any(Role.class))).thenReturn(testRole);

        RoleResponse response = roleService.updateRole(roleId, request);

        assertThat(response.getDescription()).isEqualTo("New updated description");
    }

    @Test
    void deleteRole_Success() {
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(testRole));
        when(userRoleRepository.existsByIdRoleId(roleId)).thenReturn(false);

        roleService.deleteRole(roleId);

        verify(roleRepository).delete(testRole);
    }

    @Test
    void deleteRole_InUse_ThrowsException() {
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(testRole));
        when(userRoleRepository.existsByIdRoleId(roleId)).thenReturn(true);

        assertThatThrownBy(() -> roleService.deleteRole(roleId))
                .isInstanceOf(RoleInUseException.class);
    }
}

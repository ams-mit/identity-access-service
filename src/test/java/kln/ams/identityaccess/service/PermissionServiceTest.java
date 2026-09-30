package kln.ams.identityaccess.service;

import kln.ams.identityaccess.dto.permission.PermissionResponse;
import kln.ams.identityaccess.dto.permission.ReplaceRolePermissionsRequest;
import kln.ams.identityaccess.dto.permission.RolePermissionsResponse;
import kln.ams.identityaccess.entity.Permission;
import kln.ams.identityaccess.entity.Role;
import kln.ams.identityaccess.exception.PermissionNotFoundException;
import kln.ams.identityaccess.exception.RoleNotFoundException;
import kln.ams.identityaccess.repository.PermissionRepository;
import kln.ams.identityaccess.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private PermissionService permissionService;

    private Permission testPerm;
    private Role testRole;
    private UUID permId;
    private UUID roleId;

    @BeforeEach
    void setUp() {
        permId = UUID.randomUUID();
        roleId = UUID.randomUUID();

        testPerm = Permission.builder()
                .id(permId)
                .code("USER_MANAGE")
                .description("Manage users")
                .active(true)
                .build();

        testRole = Role.builder()
                .id(roleId)
                .name("SYSTEM_ADMINISTRATOR")
                .permissions(new HashSet<>())
                .build();
    }

    @Test
    void getPermissions_ReturnsList() {
        when(permissionRepository.findAll()).thenReturn(List.of(testPerm));

        List<PermissionResponse> list = permissionService.getPermissions();

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getCode()).isEqualTo("USER_MANAGE");
    }

    @Test
    void getRolePermissions_Success() {
        testRole.getPermissions().add(testPerm);
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(testRole));

        RolePermissionsResponse response = permissionService.getRolePermissions(roleId);

        assertThat(response.getRoleId()).isEqualTo(roleId);
        assertThat(response.getPermissions()).hasSize(1);
        assertThat(response.getPermissions().get(0).getCode()).isEqualTo("USER_MANAGE");
    }

    @Test
    void replaceRolePermissions_Success() {
        ReplaceRolePermissionsRequest request = new ReplaceRolePermissionsRequest(List.of(permId));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(testRole));
        when(permissionRepository.findById(permId)).thenReturn(Optional.of(testPerm));

        RolePermissionsResponse response = permissionService.replaceRolePermissions(roleId, request);

        assertThat(response.getRoleId()).isEqualTo(roleId);
        assertThat(response.getPermissions()).hasSize(1);
        assertThat(response.getPermissions().get(0).getCode()).isEqualTo("USER_MANAGE");
        verify(roleRepository).save(testRole);
    }

    @Test
    void replaceRolePermissions_PermissionNotFound_ThrowsException() {
        ReplaceRolePermissionsRequest request = new ReplaceRolePermissionsRequest(List.of(permId));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(testRole));
        when(permissionRepository.findById(permId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> permissionService.replaceRolePermissions(roleId, request))
                .isInstanceOf(PermissionNotFoundException.class);
    }
}

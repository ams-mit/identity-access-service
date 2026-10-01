package kln.ams.identityaccess.service;

import kln.ams.identityaccess.dto.permission.PermissionResponse;
import kln.ams.identityaccess.dto.permission.ReplaceRolePermissionsRequest;
import kln.ams.identityaccess.dto.permission.RolePermissionsResponse;
import kln.ams.identityaccess.entity.Permission;
import kln.ams.identityaccess.entity.Role;
import kln.ams.identityaccess.exception.PermissionNotFoundException;
import kln.ams.identityaccess.exception.RoleNotFoundException;
import kln.ams.identityaccess.exception.ValidationException;
import kln.ams.identityaccess.repository.PermissionRepository;
import kln.ams.identityaccess.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;

    @Transactional(readOnly = true)
    public List<PermissionResponse> getPermissions() {
        return permissionRepository.findAll().stream()
                .map(this::toPermissionResponse)
                .sorted(Comparator.comparing(PermissionResponse::getCode))
                .toList();
    }

    @Transactional(readOnly = true)
    public RolePermissionsResponse getRolePermissions(UUID roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException("Role not found: " + roleId));

        List<PermissionResponse> permissions = role.getPermissions() != null
                ? role.getPermissions().stream()
                        .map(this::toPermissionResponse)
                        .sorted(Comparator.comparing(PermissionResponse::getCode))
                        .toList()
                : List.of();

        return RolePermissionsResponse.builder()
                .roleId(roleId)
                .roleName(role.getName())
                .permissions(permissions)
                .build();
    }

    @Transactional
    public RolePermissionsResponse replaceRolePermissions(UUID roleId, ReplaceRolePermissionsRequest request) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException("Role not found: " + roleId));

        if (request.getPermissionIds() == null || request.getPermissionIds().isEmpty()) {
            throw new ValidationException("permissionIds", "Permission IDs list cannot be empty");
        }

        Set<UUID> normalizedIds = new LinkedHashSet<>(request.getPermissionIds());
        Set<Permission> targetPermissions = new HashSet<>();
        for (UUID permId : normalizedIds) {
            if (permId == null) {
                throw new ValidationException("permissionIds", "Permission ID cannot be null");
            }
            Permission permission = permissionRepository.findById(permId)
                    .orElseThrow(() -> new PermissionNotFoundException("Permission not found with ID: " + permId));
            targetPermissions.add(permission);
        }

        role.setPermissions(targetPermissions);
        roleRepository.save(role);
        log.info("Updated permissions for role '{}' (id: {}): {} permissions assigned", role.getName(), roleId, targetPermissions.size());

        List<PermissionResponse> permissions = targetPermissions.stream()
                .map(this::toPermissionResponse)
                .sorted(Comparator.comparing(PermissionResponse::getCode))
                .toList();

        return RolePermissionsResponse.builder()
                .roleId(roleId)
                .roleName(role.getName())
                .permissions(permissions)
                .build();
    }

    private PermissionResponse toPermissionResponse(Permission permission) {
        return PermissionResponse.builder()
                .id(permission.getId())
                .code(permission.getCode())
                .description(permission.getDescription())
                .active(permission.isActive())
                .build();
    }
}

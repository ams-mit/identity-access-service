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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;

    @Transactional(readOnly = true)
    public List<RoleResponse> getRoles() {
        return roleRepository.findAll().stream()
                .map(this::toRoleResponse)
                .toList();
    }

    @Transactional
    public RoleResponse createRole(CreateRoleRequest request) {
        String roleName = request.getName().trim().toUpperCase(Locale.ROOT);

        if (roleRepository.existsByNameIgnoreCase(roleName)) {
            throw new DuplicateResourceException("ROLE_ALREADY_EXISTS", "Role already exists: " + roleName);
        }

        Role role = Role.builder()
                .name(roleName)
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .active(true)
                .permissions(new HashSet<>())
                .build();

        Role savedRole = roleRepository.save(role);
        log.info("Created role '{}' (id: {})", savedRole.getName(), savedRole.getId());
        return toRoleResponse(savedRole);
    }

    @Transactional(readOnly = true)
    public RoleResponse getRole(UUID roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException("Role not found: " + roleId));
        return toRoleResponse(role);
    }

    @Transactional
    public RoleResponse updateRole(UUID roleId, UpdateRoleRequest request) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException("Role not found: " + roleId));

        if (request.getDescription() != null) {
            role.setDescription(request.getDescription().trim());
        }

        Role savedRole = roleRepository.save(role);
        log.info("Updated description for role id {}", roleId);
        return toRoleResponse(savedRole);
    }

    @Transactional
    public void deleteRole(UUID roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException("Role not found: " + roleId));

        if (userRoleRepository.existsByIdRoleId(roleId)) {
            log.warn("Cannot delete role '{}' (id: {}): role is currently assigned to users", role.getName(), roleId);
            throw new RoleInUseException("Role cannot be deleted while referenced by active user assignments");
        }

        roleRepository.delete(role);
        log.info("Deleted role '{}' (id: {})", role.getName(), roleId);
    }

    private RoleResponse toRoleResponse(Role role) {
        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .active(role.isActive())
                .build();
    }
}

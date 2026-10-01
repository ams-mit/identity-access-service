package kln.ams.identityaccess.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kln.ams.identityaccess.dto.response.ApiErrorResponse;
import kln.ams.identityaccess.dto.role.CreateRoleRequest;
import kln.ams.identityaccess.dto.role.RoleResponse;
import kln.ams.identityaccess.dto.role.UpdateRoleRequest;
import kln.ams.identityaccess.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@Tag(name = "Role Management", description = "Role definition administration (SYSTEM_ADMINISTRATOR only)")
@SecurityRequirement(name = "BearerAuth")
public class RoleController {

    private final RoleService roleService;

    @Operation(summary = "List roles", description = "ROLE-001: List all defined canonical system roles.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Roles retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<kln.ams.identityaccess.dto.response.ApiResponse<List<RoleResponse>>> listRoles() {
        List<RoleResponse> roles = roleService.getRoles();
        return ResponseEntity.ok(kln.ams.identityaccess.dto.response.ApiResponse.ok("Roles retrieved successfully", roles));
    }

    @Operation(summary = "Create role", description = "ROLE-002: Define a new role definition.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Role created successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Role already exists", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<kln.ams.identityaccess.dto.response.ApiResponse<RoleResponse>> createRole(
            @Valid @RequestBody CreateRoleRequest request) {
        RoleResponse response = roleService.createRole(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(kln.ams.identityaccess.dto.response.ApiResponse.ok("Role created successfully", response));
    }

    @Operation(summary = "Get role", description = "ROLE-003: Retrieve details of a specific role by UUID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Role retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Role not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{roleId}")
    public ResponseEntity<kln.ams.identityaccess.dto.response.ApiResponse<RoleResponse>> getRole(
            @Parameter(description = "Role UUID") @PathVariable UUID roleId) {
        RoleResponse response = roleService.getRole(roleId);
        return ResponseEntity.ok(kln.ams.identityaccess.dto.response.ApiResponse.ok("Role retrieved successfully", response));
    }

    @Operation(summary = "Update role", description = "ROLE-004: Update role description.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Role updated successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Role not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @RequestMapping(value = "/{roleId}", method = {org.springframework.web.bind.annotation.RequestMethod.PATCH, org.springframework.web.bind.annotation.RequestMethod.PUT})
    public ResponseEntity<kln.ams.identityaccess.dto.response.ApiResponse<RoleResponse>> updateRole(
            @Parameter(description = "Role UUID") @PathVariable UUID roleId,
            @Valid @RequestBody UpdateRoleRequest request) {
        RoleResponse response = roleService.updateRole(roleId, request);
        return ResponseEntity.ok(kln.ams.identityaccess.dto.response.ApiResponse.ok("Role updated successfully", response));
    }

    @Operation(summary = "Delete role", description = "ROLE-005: Remove a role definition when not referenced by active assignments.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Role deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Role not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Role in use by users", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @DeleteMapping("/{roleId}")
    public ResponseEntity<Void> deleteRole(
            @Parameter(description = "Role UUID") @PathVariable UUID roleId) {
        roleService.deleteRole(roleId);
        return ResponseEntity.noContent().build();
    }
}

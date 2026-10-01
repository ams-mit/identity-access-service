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
import kln.ams.identityaccess.dto.permission.PermissionResponse;
import kln.ams.identityaccess.dto.permission.ReplaceRolePermissionsRequest;
import kln.ams.identityaccess.dto.permission.RolePermissionsResponse;
import kln.ams.identityaccess.dto.response.ApiErrorResponse;
import kln.ams.identityaccess.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping
@RequiredArgsConstructor
@Tag(name = "Permission Management", description = "Permission catalog and role-permission mappings (SYSTEM_ADMINISTRATOR only)")
@SecurityRequirement(name = "BearerAuth")
public class PermissionController {

    private final PermissionService permissionService;

    @Operation(summary = "List permissions", description = "PERM-001: List all defined granular permissions.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Permissions retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/api/v1/permissions")
    public ResponseEntity<kln.ams.identityaccess.dto.response.ApiResponse<List<PermissionResponse>>> listPermissions() {
        List<PermissionResponse> permissions = permissionService.getPermissions();
        return ResponseEntity.ok(kln.ams.identityaccess.dto.response.ApiResponse.ok("Permissions retrieved successfully", permissions));
    }

    @Operation(summary = "Get role permissions", description = "PERM-002: Retrieve permissions mapped to a specific role.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Role permissions retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Role not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/api/v1/roles/{roleId}/permissions")
    public ResponseEntity<kln.ams.identityaccess.dto.response.ApiResponse<RolePermissionsResponse>> getRolePermissions(
            @Parameter(description = "Role UUID") @PathVariable UUID roleId) {
        RolePermissionsResponse response = permissionService.getRolePermissions(roleId);
        return ResponseEntity.ok(kln.ams.identityaccess.dto.response.ApiResponse.ok("Role permissions retrieved successfully", response));
    }

    @Operation(summary = "Replace role permissions", description = "PERM-003: Atomically replace the permissions mapped to a role.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Role permissions replaced successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Role not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Permission not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PutMapping("/api/v1/roles/{roleId}/permissions")
    public ResponseEntity<kln.ams.identityaccess.dto.response.ApiResponse<RolePermissionsResponse>> replaceRolePermissions(
            @Parameter(description = "Role UUID") @PathVariable UUID roleId,
            @Valid @RequestBody ReplaceRolePermissionsRequest request) {
        RolePermissionsResponse response = permissionService.replaceRolePermissions(roleId, request);
        return ResponseEntity.ok(kln.ams.identityaccess.dto.response.ApiResponse.ok("Role permissions replaced successfully", response));
    }
}

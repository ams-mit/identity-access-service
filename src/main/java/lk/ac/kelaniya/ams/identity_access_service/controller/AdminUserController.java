package lk.ac.kelaniya.ams.identity_access_service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.ac.kelaniya.ams.identity_access_service.dto.request.AssignRoleRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.request.UpdateAccountStatusRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.AdminUserDetailResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.AdminUserSummaryResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.ErrorResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lk.ac.kelaniya.ams.identity_access_service.exception.SelfRoleAssignmentException;
import lk.ac.kelaniya.ams.identity_access_service.security.UserPrincipal;
import lk.ac.kelaniya.ams.identity_access_service.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Administrative REST controller for user search, listing, and detailed profile inspection.
 * Restricted strictly to users holding the SYSTEM_ADMINISTRATOR role.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMINISTRATOR')")
@Tag(name = "Admin - User Management", description = "Administrative endpoints for user search, listing, and inspection")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMINISTRATOR')")
    @Operation(
            summary = "Search and list users with pagination and filters",
            description = "Retrieves a paginated list of user summaries. Supports free-text search on name and email, "
                    + "filtering by account status, and filtering by requested role for batch review of pending registrations. "
                    + "Access is restricted strictly to users with the SYSTEM_ADMINISTRATOR role."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Users retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - missing, invalid, or expired Bearer token",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - requires SYSTEM_ADMINISTRATOR role",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<Page<AdminUserSummaryResponse>> searchUsers(
            @Parameter(
                    description = "Free-text search query matching case-insensitively against first name, last name, full name, or email",
                    example = "john"
            )
            @RequestParam(required = false) String query,

            @Parameter(
                    description = "Filter by account lifecycle status (e.g. PENDING_VERIFICATION, ACTIVE, SUSPENDED, DEACTIVATED)",
                    example = "PENDING_VERIFICATION"
            )
            @RequestParam(required = false) AccountStatus status,

            @Parameter(
                    description = "Filter by advisory requested role (e.g. OWNER, TENANT_RESIDENT, TECHNICIAN). "
                            + "Enables administrators to batch-review pending account requests grouped by intended role.",
                    example = "OWNER"
            )
            @RequestParam(required = false) String requestedRole,

            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        Page<AdminUserSummaryResponse> users = adminUserService.searchUsers(query, status, requestedRole, pageable);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('SYSTEM_ADMINISTRATOR')")
    @Operation(
            summary = "Get detailed user record for administrative review",
            description = "Retrieves the full user record including all profile fields, timestamps, lockout status, "
                    + "and explicitly highlights the requestedRole field for administrator verification and role assignment decisions. "
                    + "Access is restricted strictly to users with the SYSTEM_ADMINISTRATOR role."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "User details retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminUserDetailResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - missing, invalid, or expired Bearer token",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - requires SYSTEM_ADMINISTRATOR role",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found with the specified ID",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<AdminUserDetailResponse> getUserById(
            @Parameter(description = "Unique user identifier (UUID)", required = true, example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
            @PathVariable UUID userId
    ) {
        AdminUserDetailResponse user = adminUserService.getUserById(userId);
        return ResponseEntity.ok(user);
    }

    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasRole('SYSTEM_ADMINISTRATOR')")
    @Operation(
            summary = "Update user account lifecycle status",
            description = "Transitions a user's account status according to strict state machine rules. "
                    + "Allowed transitions: PENDING_VERIFICATION -> [ACTIVE, REJECTED], ACTIVE -> [SUSPENDED, DEACTIVATED], "
                    + "SUSPENDED -> [ACTIVE, DEACTIVATED]. A non-blank reason is required when transitioning to SUSPENDED, "
                    + "DEACTIVATED, or REJECTED. Access is restricted strictly to users with the SYSTEM_ADMINISTRATOR role."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Account status updated successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminUserDetailResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad request - invalid status transition or missing mandatory reason",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - missing, invalid, or expired Bearer token",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - requires SYSTEM_ADMINISTRATOR role",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found with the specified ID",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<AdminUserDetailResponse> updateAccountStatus(
            @Parameter(description = "Unique user identifier (UUID)", required = true, example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateAccountStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID adminId = principal != null ? principal.getUserId() : null;
        AdminUserDetailResponse response = adminUserService.updateAccountStatus(userId, request, adminId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{userId}/roles")
    @PreAuthorize("hasRole('SYSTEM_ADMINISTRATOR')")
    @Operation(
            summary = "Assign role to user",
            description = "Grants a staff or system role to a user account by creating a UserRole record. "
                    + "A user may hold multiple roles simultaneously (existing roles are not removed). "
                    + "The assigned role is not constrained to match the user's requestedRole. "
                    + "Self-assignment by an administrator is strictly prohibited. "
                    + "Access is restricted strictly to users with the SYSTEM_ADMINISTRATOR role."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Role assigned successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminUserDetailResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad request - invalid or unrecognized role name",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - missing, invalid, or expired Bearer token",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - requires SYSTEM_ADMINISTRATOR role or attempting self-assignment",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found with the specified ID",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflict - user already holds the specified role",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<AdminUserDetailResponse> assignRole(
            @Parameter(description = "Unique user identifier (UUID)", required = true, example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
            @PathVariable UUID userId,
            @Valid @RequestBody AssignRoleRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID adminId = principal != null ? principal.getUserId() : null;
        if (adminId != null && adminId.equals(userId)) {
            throw new SelfRoleAssignmentException("Administrators cannot assign roles to their own account.");
        }
        AdminUserDetailResponse response = adminUserService.assignRole(userId, request.getRole(), adminId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{userId}/roles/{roleName}")
    @PreAuthorize("hasRole('SYSTEM_ADMINISTRATOR')")
    @Operation(
            summary = "Remove role from user",
            description = "Removes a specific granted role from a user account. "
                    + "Self-removal by an administrator is strictly prohibited. "
                    + "Access is restricted strictly to users with the SYSTEM_ADMINISTRATOR role."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Role removed successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminUserDetailResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad request - invalid or unrecognized role name",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - missing, invalid, or expired Bearer token",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - requires SYSTEM_ADMINISTRATOR role or attempting self-removal",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found with the specified ID",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflict - user does not hold the specified role",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<AdminUserDetailResponse> removeRole(
            @Parameter(description = "Unique user identifier (UUID)", required = true, example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
            @PathVariable UUID userId,
            @Parameter(description = "Role name to remove", required = true, example = "FINANCE_OFFICER")
            @PathVariable String roleName,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID adminId = principal != null ? principal.getUserId() : null;
        if (adminId != null && adminId.equals(userId)) {
            throw new SelfRoleAssignmentException("Administrators cannot remove roles from their own account.");
        }
        AdminUserDetailResponse response = adminUserService.removeRole(userId, roleName, adminId);
        return ResponseEntity.ok(response);
    }
}

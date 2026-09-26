package lk.ac.kelaniya.ams.identity_access_service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.ac.kelaniya.ams.identity_access_service.dto.request.AdminCreateUserRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.request.AssignRoleRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.request.UpdateAccountStatusRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.AdminCreateUserResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.AdminUserDetailResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.AdminUserSummaryResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.ApiResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.ErrorResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.PagedMeta;
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
import org.springframework.http.HttpStatus;
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

import java.util.List;
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

    @PostMapping
    @PreAuthorize("hasRole('SYSTEM_ADMINISTRATOR')")
    @Operation(
            summary = "Create user account by administrator",
            description = "Creates a new user account with ACTIVE status and an initial temporary password.\n\n"
                    + "- **Forced Password Change**: The temporary password immediately flags `mustChangePassword = true`, forcing the user to change their password upon initial login.\n"
                    + "- **Separate Role Assignment**: Creation produces a base account with zero granted roles; system and staff roles must be assigned separately via `POST /api/v1/users/{userId}/roles`.\n\n"
                    + "Access is restricted strictly to users with the `SYSTEM_ADMINISTRATOR` role."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "User account created successfully",
                    content = @Content(mediaType = "application/json")
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Bad request - validation failure on request payload or weak temporary password",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - missing, invalid, or expired Bearer token",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - requires SYSTEM_ADMINISTRATOR role",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Conflict - email already registered",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<AdminCreateUserResponse>> createUser(
            @Valid @RequestBody AdminCreateUserRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID adminId = principal != null ? principal.getUserId() : null;
        AdminCreateUserResponse response = adminUserService.createUser(request, adminId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }

    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMINISTRATOR')")
    @Operation(
            summary = "Search and list users with pagination and filters",
            description = "Retrieves a paginated list of user summaries. Supports free-text search across name and email, "
                    + "filtering by account lifecycle status, and filtering by advisory requested role for batch review of pending registrations.\n\n"
                    + "Access is restricted strictly to users with the `SYSTEM_ADMINISTRATOR` role."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Users retrieved successfully",
                    content = @Content(mediaType = "application/json")
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Bad request - invalid filter parameter or pagination argument",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - missing, invalid, or expired Bearer token",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - requires SYSTEM_ADMINISTRATOR role",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<List<AdminUserSummaryResponse>>> searchUsers(
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
                    description = "Filter by advisory requested role (e.g. OWNER, TENANT_RESIDENT). "
                            + "Specifically enables administrators to batch-review pending registration requests grouped by intended role "
                            + "prior to formal account approval and role assignment.",
                    example = "OWNER"
            )
            @RequestParam(required = false) String requestedRole,

            @Parameter(
                    description = "Filter by assigned role (e.g. SYSTEM_ADMINISTRATOR, OWNER, TENANT_RESIDENT, FINANCE_OFFICER)",
                    example = "SYSTEM_ADMINISTRATOR"
            )
            @RequestParam(required = false) String role,

            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        Page<AdminUserSummaryResponse> users = adminUserService.searchUsers(query, status, requestedRole, role, pageable);
        PagedMeta meta = PagedMeta.builder()
                .page(users.getNumber())
                .size(users.getSize())
                .totalElements(users.getTotalElements())
                .build();
        return ResponseEntity.ok(ApiResponse.of(users.getContent(), meta));
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
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "User details retrieved successfully",
                    content = @Content(mediaType = "application/json")
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - missing, invalid, or expired Bearer token",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - requires SYSTEM_ADMINISTRATOR role",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "User not found with the specified ID",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> getUserById(
            @Parameter(description = "Unique user identifier (UUID)", required = true, example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
            @PathVariable UUID userId
    ) {
        AdminUserDetailResponse user = adminUserService.getUserById(userId);
        return ResponseEntity.ok(ApiResponse.of(user));
    }

    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasRole('SYSTEM_ADMINISTRATOR')")
    @Operation(
            summary = "Update user account lifecycle status",
            description = "Transitions a user's account lifecycle status according to the strict state machine matrix:\n\n"
                    + "- **PENDING_VERIFICATION** -> **ACTIVE** (reason optional), **REJECTED** (reason required)\n"
                    + "- **ACTIVE** -> **SUSPENDED** (reason required), **DEACTIVATED** (reason required)\n"
                    + "- **SUSPENDED** -> **ACTIVE** (reason optional), **DEACTIVATED** (reason required)\n\n"
                    + "All other status transitions are invalid and rejected with HTTP 400 (`INVALID_STATUS_TRANSITION`).\n"
                    + "A non-blank reason is strictly mandatory when transitioning to `SUSPENDED`, `DEACTIVATED`, or `REJECTED`.\n\n"
                    + "- **Email Notifications**: Transitions from `PENDING_VERIFICATION` to `ACTIVE` (approval) or `REJECTED` (rejection with reason) trigger an automated plain-text email notification to the applicant. Email delivery is best-effort and non-blocking: delivery failure will not fail or roll back the status update transaction. No emails are sent for other status transitions (e.g. suspend or deactivate).\n\n"
                    + "Access is restricted strictly to users with the `SYSTEM_ADMINISTRATOR` role."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Account status updated successfully",
                    content = @Content(mediaType = "application/json")
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Bad request - invalid status transition or missing mandatory reason",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - missing, invalid, or expired Bearer token",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - requires SYSTEM_ADMINISTRATOR role",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "User not found with the specified ID",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> updateAccountStatus(
            @Parameter(description = "Unique user identifier (UUID)", required = true, example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateAccountStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID adminId = principal != null ? principal.getUserId() : null;
        AdminUserDetailResponse response = adminUserService.updateAccountStatus(userId, request, adminId);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @PostMapping("/{userId}/roles")
    @PreAuthorize("hasRole('SYSTEM_ADMINISTRATOR')")
    @Operation(
            summary = "Assign role to user",
            description = "Grants a staff or system role to a user account by creating a UserRole record.\n\n"
                    + "- **Additive**: A user may hold multiple roles simultaneously (existing roles are preserved).\n"
                    + "- **Independent of requestedRole**: The assigned role is independent of and not constrained by the user's advisory requestedRole submitted at registration.\n"
                    + "- **Self-Assignment Guard**: Self-assignment by an administrator to their own account is strictly prohibited and returns HTTP 403 (`FORBIDDEN`).\n\n"
                    + "Access is restricted strictly to users with the `SYSTEM_ADMINISTRATOR` role."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Role assigned successfully",
                    content = @Content(mediaType = "application/json")
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Bad request - invalid or unrecognized role name",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - missing, invalid, or expired Bearer token",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - requires SYSTEM_ADMINISTRATOR role or attempting self-assignment",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "User not found with the specified ID",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Conflict - user already holds the specified role",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> assignRole(
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
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @DeleteMapping("/{userId}/roles/{roleName}")
    @PreAuthorize("hasRole('SYSTEM_ADMINISTRATOR')")
    @Operation(
            summary = "Remove role from user",
            description = "Removes a specific granted role from a user account.\n\n"
                    + "- **Self-Removal Guard**: Self-removal by an administrator from their own account is strictly prohibited and returns HTTP 403 (`FORBIDDEN`).\n\n"
                    + "Access is restricted strictly to users with the `SYSTEM_ADMINISTRATOR` role."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Role removed successfully",
                    content = @Content(mediaType = "application/json")
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Bad request - invalid or unrecognized role name",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - missing, invalid, or expired Bearer token",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - requires SYSTEM_ADMINISTRATOR role or attempting self-removal",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "User not found with the specified ID",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Conflict - user does not hold the specified role",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> removeRole(
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
        return ResponseEntity.ok(ApiResponse.of(response));
    }
}

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
import kln.ams.identityaccess.dto.response.PaginationMetadata;
import kln.ams.identityaccess.dto.user.CreateUserRequest;
import kln.ams.identityaccess.dto.user.ReplaceUserRolesRequest;
import kln.ams.identityaccess.dto.user.UpdateStatusRequest;
import kln.ams.identityaccess.dto.user.UpdateUserRequest;
import kln.ams.identityaccess.dto.user.UserResponse;
import kln.ams.identityaccess.dto.user.UserRolesResponse;
import kln.ams.identityaccess.entity.AccountStatus;
import kln.ams.identityaccess.exception.ValidationException;
import kln.ams.identityaccess.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "User account administration (SYSTEM_ADMINISTRATOR only)")
@SecurityRequirement(name = "BearerAuth")
public class UserController {

    private final UserService userService;

    @Operation(summary = "List user accounts", description = "USR-001: Paginated retrieval of identity accounts with filtering and search.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Users retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires SYSTEM_ADMINISTRATOR", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<kln.ams.identityaccess.dto.response.ApiResponse<List<UserResponse>>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String search) {
        if (size > 100) {
            throw new ValidationException("size", "Page size must not exceed 100");
        }
        if (size < 1) {
            throw new ValidationException("size", "Page size must be at least 1");
        }
        if (page < 0) {
            throw new ValidationException("page", "Page index must not be negative");
        }
        Page<UserResponse> userPage = userService.getUsersPage(page, size, status, role, search);
        PaginationMetadata pagination = PaginationMetadata.fromPage(userPage);
        return ResponseEntity.ok(kln.ams.identityaccess.dto.response.ApiResponse.paginated(
                "Users retrieved successfully",
                userPage.getContent(),
                pagination
        ));
    }

    @Operation(summary = "Create user account", description = "USR-002: Creates a new user account with canonical role assignments.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User created successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "User or email already exists", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Invalid canonical role", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<kln.ams.identityaccess.dto.response.ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        UserResponse response = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(kln.ams.identityaccess.dto.response.ApiResponse.ok("User created successfully", response));
    }

    @Operation(summary = "Get user account", description = "USR-003: Retrieve identity account details by user ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{userId}")
    public ResponseEntity<kln.ams.identityaccess.dto.response.ApiResponse<UserResponse>> getUser(
            @Parameter(description = "User UUID") @PathVariable UUID userId) {
        UserResponse response = userService.getUser(userId);
        return ResponseEntity.ok(kln.ams.identityaccess.dto.response.ApiResponse.ok("User retrieved successfully", response));
    }

    @Operation(summary = "Update user account fields", description = "USR-004: Update contact and personal details for an existing user account.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User updated successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email already in use", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PatchMapping("/{userId}")
    public ResponseEntity<kln.ams.identityaccess.dto.response.ApiResponse<UserResponse>> updateUser(
            @Parameter(description = "User UUID") @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRequest request) {
        UserResponse response = userService.updateUser(userId, request);
        return ResponseEntity.ok(kln.ams.identityaccess.dto.response.ApiResponse.ok("User updated successfully", response));
    }

    @Operation(summary = "Update user account status", description = "USR-005: Activate, deactivate, or suspend a user account.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User status updated successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PatchMapping("/{userId}/status")
    public ResponseEntity<kln.ams.identityaccess.dto.response.ApiResponse<UserResponse>> updateStatus(
            @Parameter(description = "User UUID") @PathVariable UUID userId,
            @Valid @RequestBody UpdateStatusRequest request) {
        UserResponse response = userService.updateStatus(userId, request);
        return ResponseEntity.ok(kln.ams.identityaccess.dto.response.ApiResponse.ok("User status updated successfully", response));
    }

    @Operation(summary = "Get user roles", description = "USR-006: Retrieve canonical roles assigned to a user account.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User roles retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{userId}/roles")
    public ResponseEntity<kln.ams.identityaccess.dto.response.ApiResponse<UserRolesResponse>> getUserRoles(
            @Parameter(description = "User UUID") @PathVariable UUID userId) {
        UserRolesResponse response = userService.getUserRoles(userId);
        return ResponseEntity.ok(kln.ams.identityaccess.dto.response.ApiResponse.ok("User roles retrieved successfully", response));
    }

    @Operation(summary = "Replace user roles", description = "USR-007: Atomically replace the set of canonical roles assigned to a user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User roles replaced successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Invalid role", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PutMapping("/{userId}/roles")
    public ResponseEntity<kln.ams.identityaccess.dto.response.ApiResponse<UserRolesResponse>> replaceUserRoles(
            @Parameter(description = "User UUID") @PathVariable UUID userId,
            @Valid @RequestBody ReplaceUserRolesRequest request) {
        UserRolesResponse response = userService.replaceUserRoles(userId, request);
        return ResponseEntity.ok(kln.ams.identityaccess.dto.response.ApiResponse.ok("User roles replaced successfully", response));
    }
}

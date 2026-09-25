package lk.ac.kelaniya.ams.identity_access_service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.ac.kelaniya.ams.identity_access_service.dto.request.ChangePasswordRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.ErrorResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.UserSummaryResponse;
import lk.ac.kelaniya.ams.identity_access_service.exception.InvalidCredentialsException;
import lk.ac.kelaniya.ams.identity_access_service.security.ServicePrincipal;
import lk.ac.kelaniya.ams.identity_access_service.security.UserPrincipal;
import lk.ac.kelaniya.ams.identity_access_service.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for authenticated user operations and profile management.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Profile", description = "User profile and account operations")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(
            summary = "Get current authenticated user profile",
            description = "Retrieves fresh user profile data from the database for the authenticated user principal. Rejects requests if token is missing/invalid, user no longer exists, or account is deactivated.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "User profile retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserSummaryResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - missing, invalid, or expired Bearer token",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - user account is deactivated",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Not found - user account does not exist",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<UserSummaryResponse> getCurrentUser(@AuthenticationPrincipal Object principal) {
        if (principal instanceof ServicePrincipal) {
            throw new InvalidCredentialsException("Authentication required");
        }
        if (!(principal instanceof UserPrincipal userPrincipal) || userPrincipal.getUserId() == null) {
            throw new InvalidCredentialsException("Authentication required");
        }
        UserSummaryResponse response = userService.getCurrentUser(userPrincipal.getUserId());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/me/password")
    @Operation(
            summary = "Change password for authenticated user",
            description = "Self-service password update for the currently authenticated user. Validates current password, enforces password complexity policy, verifies new password differs from current, updates password hash, and clears the mustChangePassword flag.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "Password changed successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad request - validation failure, password mismatch, or new password same as current password",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - unauthenticated, invalid token, or current password incorrect",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - user account is deactivated",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Not found - user account does not exist",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Object principal,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        if (principal instanceof ServicePrincipal) {
            throw new InvalidCredentialsException("Authentication required");
        }
        if (!(principal instanceof UserPrincipal userPrincipal) || userPrincipal.getUserId() == null) {
            throw new InvalidCredentialsException("Authentication required");
        }
        userService.changePassword(userPrincipal.getUserId(), request);
        return ResponseEntity.noContent().build();
    }
}

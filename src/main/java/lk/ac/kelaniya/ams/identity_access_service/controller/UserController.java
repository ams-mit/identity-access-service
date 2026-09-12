package lk.ac.kelaniya.ams.identity_access_service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.ErrorResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.UserSummaryResponse;
import lk.ac.kelaniya.ams.identity_access_service.exception.InvalidCredentialsException;
import lk.ac.kelaniya.ams.identity_access_service.security.UserPrincipal;
import lk.ac.kelaniya.ams.identity_access_service.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for authenticated user operations and profile management.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User", description = "User profile and account operations")
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
                    description = "Unauthorized - missing, invalid, or expired token, or user no longer exists",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - user account is deactivated",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<UserSummaryResponse> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new InvalidCredentialsException("Unauthorized - missing or invalid authentication");
        }
        UserSummaryResponse response = userService.getCurrentUser(principal.getUserId());
        return ResponseEntity.ok(response);
    }
}

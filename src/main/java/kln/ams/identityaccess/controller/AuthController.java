package kln.ams.identityaccess.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kln.ams.identityaccess.dto.auth.AuthenticatedUserResponse;
import kln.ams.identityaccess.dto.auth.LoginRequest;
import kln.ams.identityaccess.dto.auth.LoginResponse;
import kln.ams.identityaccess.dto.response.ApiErrorResponse;
import kln.ams.identityaccess.security.UserPrincipal;
import kln.ams.identityaccess.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and current account operations")
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "Authenticate user and issue User JWT",
            description = "Validates user credentials and issues an RS256-signed User JWT with subject, type=user, and canonical roles."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Account inactive or suspended",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<kln.ams.identityaccess.dto.response.ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(kln.ams.identityaccess.dto.response.ApiResponse.ok("Login successful", response));
    }

    @Operation(
            summary = "Get currently authenticated account",
            description = "Returns identity account details for the authenticated user based on the Gateway-verified token.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated account retrieved successfully",
                    content = @Content(schema = @Schema(implementation = AuthenticatedUserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Account inactive",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/me")
    public ResponseEntity<kln.ams.identityaccess.dto.response.ApiResponse<AuthenticatedUserResponse>> getCurrentUser(
            @AuthenticationPrincipal UserPrincipal principal,
            java.security.Principal servletPrincipal,
            org.springframework.security.core.Authentication authentication) {
        java.util.UUID userId = null;
        if (principal != null) {
            userId = principal.getId();
        } else if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal up) {
            userId = up.getId();
        } else if (servletPrincipal instanceof org.springframework.security.core.Authentication auth && auth.getPrincipal() instanceof UserPrincipal up) {
            userId = up.getId();
        } else if (servletPrincipal != null && servletPrincipal.getName() != null) {
            try {
                userId = java.util.UUID.fromString(servletPrincipal.getName());
            } catch (IllegalArgumentException ignored) {
            }
        } else if (authentication != null && authentication.getName() != null) {
            try {
                userId = java.util.UUID.fromString(authentication.getName());
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (userId == null) {
            throw new kln.ams.identityaccess.exception.InvalidTokenException("Missing or invalid user authentication");
        }

        AuthenticatedUserResponse response = authService.getCurrentUser(userId);
        return ResponseEntity.ok(kln.ams.identityaccess.dto.response.ApiResponse.ok("Authenticated account retrieved successfully", response));
    }
}

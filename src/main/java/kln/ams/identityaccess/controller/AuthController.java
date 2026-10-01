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
import kln.ams.identityaccess.dto.auth.ChangePasswordRequest;
import kln.ams.identityaccess.dto.auth.ForgotPasswordRequest;
import kln.ams.identityaccess.dto.auth.LoginRequest;
import kln.ams.identityaccess.dto.auth.LoginResponse;
import kln.ams.identityaccess.dto.auth.MessageResponse;
import kln.ams.identityaccess.dto.auth.RegisterRequest;
import kln.ams.identityaccess.dto.auth.RegisterResponse;
import kln.ams.identityaccess.dto.auth.ResetPasswordRequest;
import kln.ams.identityaccess.dto.response.ApiErrorResponse;
import kln.ams.identityaccess.exception.InvalidTokenException;
import kln.ams.identityaccess.security.UserPrincipal;
import kln.ams.identityaccess.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and user lifecycle management APIs")
public class AuthController {

    private final AuthService authService;

    @Operation(
            operationId = "AUTH-001",
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
            operationId = "AUTH-002",
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
            Principal servletPrincipal,
            Authentication authentication) {
        UUID userId = resolveUserId(principal, servletPrincipal, authentication);
        AuthenticatedUserResponse response = authService.getCurrentUser(userId);
        return ResponseEntity.ok(kln.ams.identityaccess.dto.response.ApiResponse.ok("Authenticated account retrieved successfully", response));
    }

    @Operation(
            operationId = "AUTH-003",
            summary = "Logout user",
            description = "Logs out authenticated user. Stateless operation adhering to JWT security standard (frontend discards token).",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Logged out successfully (no content)"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal UserPrincipal principal,
            Principal servletPrincipal,
            Authentication authentication) {
        UUID userId = resolveUserId(principal, servletPrincipal, authentication);
        authService.logout(userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            operationId = "AUTH-004",
            summary = "Self-register new user account",
            description = "Registers a new user account with default role TENANT_RESIDENT and initial status INACTIVE. Requires administrative activation via USR-005 before login."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User registered successfully",
                    content = @Content(schema = @Schema(implementation = RegisterResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "User already exists with email or username",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/register")
    public ResponseEntity<kln.ams.identityaccess.dto.response.ApiResponse<RegisterResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(kln.ams.identityaccess.dto.response.ApiResponse.ok("User registered successfully", response));
    }

    @Operation(
            operationId = "AUTH-005",
            summary = "Request password reset instructions",
            description = "Initiates password reset flow by submitting account email. Enforces strict anti-enumeration: always returns HTTP 200 with identical generic message."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password reset instructions requested (generic message returned unconditionally)",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error on email format",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/forgot-password")
    public ResponseEntity<kln.ams.identityaccess.dto.response.ApiResponse<MessageResponse>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        MessageResponse response = authService.forgotPassword(request);
        return ResponseEntity.ok(kln.ams.identityaccess.dto.response.ApiResponse.ok(response.getMessage(), response));
    }

    @Operation(
            operationId = "AUTH-006",
            summary = "Reset password using reset token",
            description = "Resets user password using a cryptographically secure reset token. Enforces anti-enumeration: all invalid, expired, and already-used token cases return an identical generic 400 error."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password reset successfully",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid or expired reset token, password mismatch, or validation failure",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/reset-password")
    public ResponseEntity<kln.ams.identityaccess.dto.response.ApiResponse<MessageResponse>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        MessageResponse response = authService.resetPassword(request);
        return ResponseEntity.ok(kln.ams.identityaccess.dto.response.ApiResponse.ok(response.getMessage(), response));
    }

    @Operation(
            operationId = "AUTH-007",
            summary = "Change own password",
            description = "Self-service password update for the currently authenticated user. Validates current password, enforces password policy, verifies new password differs from current, and updates BCrypt password hash.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Password changed successfully (no content)"),
            @ApiResponse(responseCode = "400", description = "Validation error, password mismatch, same password, or incorrect current password",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            Principal servletPrincipal,
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {
        UUID userId = resolveUserId(principal, servletPrincipal, authentication);
        authService.changePassword(userId, request);
        return ResponseEntity.noContent().build();
    }

    private UUID resolveUserId(UserPrincipal principal, Principal servletPrincipal, Authentication authentication) {
        UUID userId = null;
        if (principal != null) {
            userId = principal.getId();
        } else if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal up) {
            userId = up.getId();
        } else if (servletPrincipal instanceof Authentication auth && auth.getPrincipal() instanceof UserPrincipal up) {
            userId = up.getId();
        } else if (servletPrincipal != null && servletPrincipal.getName() != null) {
            try {
                userId = UUID.fromString(servletPrincipal.getName());
            } catch (IllegalArgumentException ignored) {
            }
        } else if (authentication != null && authentication.getName() != null) {
            try {
                userId = UUID.fromString(authentication.getName());
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (userId == null) {
            throw new InvalidTokenException("Missing or invalid user authentication");
        }
        return userId;
    }
}

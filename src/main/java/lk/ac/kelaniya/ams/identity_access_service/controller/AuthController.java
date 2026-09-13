package lk.ac.kelaniya.ams.identity_access_service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.ac.kelaniya.ams.identity_access_service.dto.request.LoginRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.request.RegisterRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.ErrorResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.LoginResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.RegisterResponse;
import lk.ac.kelaniya.ams.identity_access_service.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller providing user registration and authentication endpoints.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and user lifecycle management APIs")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(
            summary = "Register a new user",
            description = "Registers a new user account with PENDING_VERIFICATION status and an advisory requested role "
                    + "(SYSTEM_ADMINISTRATOR, APARTMENT_MANAGER, OWNER, TENANT_RESIDENT, FINANCE_OFFICER, "
                    + "MAINTENANCE_COORDINATOR, TECHNICIAN, SECURITY_OFFICER). "
                    + "The requested role is advisory only for administrative review and grants zero permissions or roles upon registration."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "User registered successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = RegisterResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failure (e.g. invalid requested role, invalid email, weak password) or password mismatch",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Email already in use",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(
            summary = "Authenticate user",
            description = "Authenticates user credentials and issues an RS256-signed JWT token. Rejects non-active accounts."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "User authenticated successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = LoginResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failure",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Invalid email or password",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Account is not active (pending verification, suspended, or deactivated)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "423",
                    description = "Account is temporarily locked due to consecutive failed login attempts",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Logout user",
            description = "Logs out the authenticated user. In the current stateless JWT architecture, the client discards the token. Server-side token revocation/blocklisting is out of scope for Sprint 1 and would require Redis or a database-backed denylist if needed in future."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "Logged out successfully (no content)"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - missing or invalid Bearer token",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<Void> logout() {
        // Token revocation/blocklisting is out of scope for Sprint 1.
        // In this stateless JWT model, the client clears the token locally.
        // A distributed blocklist (e.g. Redis or DB-backed denylist) would be implemented if server-side revocation is required in later sprints.
        return ResponseEntity.noContent().build();
    }
}

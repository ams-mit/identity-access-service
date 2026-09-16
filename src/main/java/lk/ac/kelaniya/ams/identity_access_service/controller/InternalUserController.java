package lk.ac.kelaniya.ams.identity_access_service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.ErrorResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.InternalUserResponse;
import lk.ac.kelaniya.ams.identity_access_service.service.InternalUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Internal REST controller exposing identity and authorization validation endpoints
 * strictly for service-to-service communication.
 * Not routed through public API Gateway paths.
 */
@RestController
@RequestMapping("/internal/v1/users")
@RequiredArgsConstructor
@Tag(
        name = "Internal - Service to Service",
        description = "Internal service-to-service validation endpoints restricted strictly to trusted caller microservices holding a valid Service JWT. Never exposed through public API Gateway routes."
)
public class InternalUserController {

    private final InternalUserService internalUserService;

    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('SERVICE')")
    @Operation(
            summary = "Validate and retrieve user authorization details (Internal)",
            description = "Retrieves minimal authorization data (userId, accountStatus, roles) for a specific user ID. Restricted exclusively to internal microservices presenting a valid RS256 Service JWT. Normal user tokens (including administrators) are forbidden.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "User authorization details retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InternalUserResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - missing, invalid, or expired Service Bearer token",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - caller does not possess the SERVICE authority (user tokens strictly denied)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Not found - requested user ID does not exist",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<InternalUserResponse> getUserForValidation(
            @Parameter(description = "Unique user identifier to validate", required = true, example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
            @PathVariable UUID userId
    ) {
        InternalUserResponse response = internalUserService.getUserForValidation(userId);
        return ResponseEntity.ok(response);
    }
}

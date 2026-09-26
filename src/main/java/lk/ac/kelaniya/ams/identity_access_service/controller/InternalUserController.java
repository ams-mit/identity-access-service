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
import lk.ac.kelaniya.ams.identity_access_service.security.InternalCallerAuthorizationService;
import lk.ac.kelaniya.ams.identity_access_service.security.ServicePrincipal;
import lk.ac.kelaniya.ams.identity_access_service.service.InternalUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
@Slf4j
@RestController
@RequestMapping("/internal/v1/users")
@RequiredArgsConstructor
@Tag(
        name = "Internal - Service to Service",
        description = "Internal service-to-service validation endpoints restricted strictly to trusted caller microservices holding a valid Service JWT. Never exposed through public API Gateway routes."
)
public class InternalUserController {

    private final InternalUserService internalUserService;
    private final InternalCallerAuthorizationService internalCallerAuthorizationService;

    /**
     * Validates and retrieves user authorization details for an internal caller service.
     * Rejects missing/invalid tokens and User JWTs with HTTP 401 Unauthorized (per Standard §25).
     * Rejects valid service tokens whose calling service is not in the allow-list with HTTP 403 Forbidden.
     *
     * @param userId Unique user identifier to validate
     * @return User authorization details (userId, accountStatus, roles)
     */
    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('SERVICE')")
    @Operation(
            summary = "Validate and retrieve user authorization details (Internal)",
            description = "Retrieves minimal authorization data (userId, accountStatus, roles) for a specific user ID. "
                    + "Restricted exclusively to internal microservices presenting a valid RS256 Service JWT (granting the ROLE_SERVICE authority). "
                    + "Authentication failures (missing, invalid, or expired tokens, or User JWTs presented instead of a Service JWT) return HTTP 401 Unauthorized. "
                    + "HTTP 403 Forbidden is returned only when a valid service token is presented by a service not in the allow-list.",
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
                    description = "Unauthorized - missing, invalid, expired token, or User JWT supplied instead of Service JWT",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - valid service token presented, but calling service is not in the allow-list",
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
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String callerServiceName = (auth != null && auth.getPrincipal() instanceof ServicePrincipal sp)
                ? sp.getServiceName()
                : null;

        if (callerServiceName == null || !internalCallerAuthorizationService.isAllowed(callerServiceName, InternalCallerAuthorizationService.USER_VALIDATION_ENDPOINT)) {
            log.warn("Access denied: Caller service '{}' is not authorized to access internal endpoint '/internal/v1/users/{}'",
                    callerServiceName != null ? callerServiceName : "anonymous", userId);
            throw new AccessDeniedException(
                    "Caller service '" + callerServiceName + "' is not authorized to access internal endpoint 'user-validation'"
            );
        }

        InternalUserResponse response = internalUserService.getUserForValidation(userId);
        return ResponseEntity.ok(response);
    }
}

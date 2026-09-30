package kln.ams.identityaccess.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import kln.ams.identityaccess.dto.internal.UserStatusResponse;
import kln.ams.identityaccess.dto.internal.UserValidationResponse;
import kln.ams.identityaccess.dto.response.ApiErrorResponse;
import kln.ams.identityaccess.exception.CallerServiceNotAllowedException;
import kln.ams.identityaccess.security.InternalCallerAuthorizationService;
import kln.ams.identityaccess.security.ServicePrincipal;
import kln.ams.identityaccess.service.InternalUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/internal/users")
@RequiredArgsConstructor
@Tag(name = "Internal User Provider APIs", description = "Service-to-service validation APIs (registered Project A services only)")
@SecurityRequirement(name = "BearerAuth")
public class InternalUserController {

    private final InternalUserService internalUserService;
    private final InternalCallerAuthorizationService callerAuthorizationService;

    @Operation(
            summary = "Validate user existence, account status, and roles",
            description = "IAM-INT-001: Validates that a user identity exists and is eligible for cross-service operations. Authorized backend services only."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User validation successful"),
            @ApiResponse(responseCode = "401", description = "Invalid service token", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Calling service not allowed", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{userId}/validate")
    public ResponseEntity<kln.ams.identityaccess.dto.response.ApiResponse<UserValidationResponse>> validateUser(
            @Parameter(description = "User UUID") @PathVariable UUID userId,
            @Parameter(description = "Optional canonical role to validate against user assignments") @RequestParam(required = false) String requiredRole,
            @AuthenticationPrincipal ServicePrincipal caller,
            java.security.Principal servletPrincipal,
            org.springframework.security.core.Authentication authentication) {

        String callerName = resolveCallerName(caller, servletPrincipal, authentication);
        if (!callerAuthorizationService.isAllowed(callerName, InternalCallerAuthorizationService.USER_VALIDATION_ENDPOINT)) {
            log.warn("Unauthorized internal service '{}' attempted to invoke validateUser", callerName);
            throw new CallerServiceNotAllowedException("Calling service '" + callerName + "' is not authorized for endpoint");
        }

        UserValidationResponse response = internalUserService.validateUser(userId, requiredRole);
        return ResponseEntity.ok(kln.ams.identityaccess.dto.response.ApiResponse.ok("User validation successful", response));
    }

    @Operation(
            summary = "Validate current account status",
            description = "IAM-INT-002: Obtain authoritative current user account status for cross-service authorization. Authorized backend services only."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account status retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Invalid service token", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Calling service not allowed", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{userId}/status")
    public ResponseEntity<kln.ams.identityaccess.dto.response.ApiResponse<UserStatusResponse>> getUserStatus(
            @Parameter(description = "User UUID") @PathVariable UUID userId,
            @AuthenticationPrincipal ServicePrincipal caller,
            java.security.Principal servletPrincipal,
            org.springframework.security.core.Authentication authentication) {

        String callerName = resolveCallerName(caller, servletPrincipal, authentication);
        if (!callerAuthorizationService.isAllowed(callerName, InternalCallerAuthorizationService.USER_STATUS_ENDPOINT)) {
            log.warn("Unauthorized internal service '{}' attempted to invoke getUserStatus", callerName);
            throw new CallerServiceNotAllowedException("Calling service '" + callerName + "' is not authorized for endpoint");
        }

        UserStatusResponse response = internalUserService.getUserStatus(userId);
        return ResponseEntity.ok(kln.ams.identityaccess.dto.response.ApiResponse.ok("User account status retrieved successfully", response));
    }

    private String resolveCallerName(ServicePrincipal caller, java.security.Principal servletPrincipal, org.springframework.security.core.Authentication authentication) {
        if (caller != null && caller.getServiceName() != null) {
            return caller.getServiceName();
        }
        if (authentication != null) {
            if (authentication.getPrincipal() instanceof ServicePrincipal sp) {
                return sp.getServiceName();
            }
            if (authentication.getName() != null && !authentication.getName().isBlank()) {
                return authentication.getName();
            }
        }
        if (servletPrincipal != null) {
            if (servletPrincipal instanceof org.springframework.security.core.Authentication auth && auth.getPrincipal() instanceof ServicePrincipal sp) {
                return sp.getServiceName();
            }
            if (servletPrincipal.getName() != null && !servletPrincipal.getName().isBlank()) {
                return servletPrincipal.getName();
            }
        }
        return null;
    }
}

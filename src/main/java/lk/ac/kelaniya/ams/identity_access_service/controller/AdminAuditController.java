package lk.ac.kelaniya.ams.identity_access_service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.AuditEventResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.ErrorResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.AuditEventType;
import lk.ac.kelaniya.ams.identity_access_service.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * REST controller for administrative audit trail inspection and querying.
 * Restricted exclusively to users with the SYSTEM_ADMINISTRATOR role.
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit Logs", description = "Administrative query endpoints for security and lifecycle audit trails")
public class AdminAuditController {

    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMINISTRATOR')")
    @Operation(
            summary = "Query and paginate audit log events",
            description = "Retrieves a paginated list of security and lifecycle audit records. "
                    + "Supports multi-criteria filtering by eventType, subjectUserId, actorUserId, and created date range.\n\n"
                    + "Access is restricted strictly to users holding the `SYSTEM_ADMINISTRATOR` role.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Audit events retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad request - invalid filter parameter or pagination arguments",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - missing, invalid, or expired Bearer token",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - requires SYSTEM_ADMINISTRATOR role",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<Page<AuditEventResponse>> getAuditLogs(
            @Parameter(description = "Filter by categorized audit event type", example = "ACCOUNT_STATUS_CHANGED")
            @RequestParam(required = false) AuditEventType eventType,

            @Parameter(description = "Filter by target subject user identifier (UUID)", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
            @RequestParam(required = false) UUID subjectUserId,

            @Parameter(description = "Filter by actor user identifier who initiated the action (UUID)", example = "b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e")
            @RequestParam(required = false) UUID actorUserId,

            @Parameter(description = "Filter audit records created at or after this ISO-8601 timestamp (inclusive)", example = "2026-09-01T00:00:00Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,

            @Parameter(description = "Filter audit records created at or before this ISO-8601 timestamp (inclusive)", example = "2026-09-30T23:59:59Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,

            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        Page<AuditEventResponse> logs = auditService.searchAuditLogs(
                eventType, subjectUserId, actorUserId, from, to, pageable
        );
        return ResponseEntity.ok(logs);
    }
}

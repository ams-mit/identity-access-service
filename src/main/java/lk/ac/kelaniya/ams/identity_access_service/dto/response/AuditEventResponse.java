package lk.ac.kelaniya.ams.identity_access_service.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lk.ac.kelaniya.ams.identity_access_service.entity.AuditEvent;
import lk.ac.kelaniya.ams.identity_access_service.entity.AuditEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO representing an audit event record returned by administrative audit query endpoints.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Security and lifecycle audit event record")
public class AuditEventResponse {

    @Schema(description = "Unique audit record identifier", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
    private UUID id;

    @Schema(description = "Categorized audit event type", example = "ACCOUNT_STATUS_CHANGED")
    private AuditEventType eventType;

    @Schema(description = "Target user ID that the event is about (subject)", example = "b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e")
    private UUID subjectUserId;

    @Schema(description = "User ID of administrator or actor who triggered the event (null for unauthenticated or self-actions)", example = "c3d4e5f6-a7b8-9c0d-1e2f-3a4b5c6d7e8f")
    private UUID actorUserId;

    @Schema(description = "Previous state value prior to event (e.g. previous status or role)", example = "PENDING_VERIFICATION")
    private String oldValue;

    @Schema(description = "New state value after event (e.g. updated status or newly assigned role)", example = "ACTIVE")
    private String newValue;

    @Schema(description = "Human-readable administrative reason or diagnostic note", example = "Documents verified by manager")
    private String reason;

    @Schema(description = "Timestamp when the event occurred and was recorded", example = "2026-09-17T08:00:00Z")
    private Instant createdAt;

    public static AuditEventResponse fromEntity(AuditEvent entity) {
        return AuditEventResponse.builder()
                .id(entity.getId())
                .eventType(entity.getEventType())
                .subjectUserId(entity.getSubjectUserId())
                .actorUserId(entity.getActorUserId())
                .oldValue(entity.getOldValue())
                .newValue(entity.getNewValue())
                .reason(entity.getReason())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}

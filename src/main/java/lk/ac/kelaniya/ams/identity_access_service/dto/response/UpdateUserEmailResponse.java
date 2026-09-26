package lk.ac.kelaniya.ams.identity_access_service.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response payload confirming successful user email update by an authorized internal service.
 * Excludes passwords and sensitive fields.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User email update response payload containing updated email details")
public class UpdateUserEmailResponse {

    @Schema(description = "Unique user identifier", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
    private UUID userId;

    @Schema(description = "Updated user email address", example = "resident.new@example.com")
    private String email;
}

package lk.ac.kelaniya.ams.identity_access_service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for administrative update of a user's account lifecycle status.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request payload for updating user account lifecycle status")
public class UpdateAccountStatusRequest {

    @NotNull(message = "Target account status is required")
    @Schema(
            description = "Target account lifecycle status to transition to",
            example = "ACTIVE",
            allowableValues = {"ACTIVE", "SUSPENDED", "DEACTIVATED", "REJECTED"}
    )
    private AccountStatus status;

    @Schema(
            description = "Audit reason for the status change. Required when transitioning to SUSPENDED, DEACTIVATED, or REJECTED. Optional/ignored for ACTIVE.",
            example = "User identity documents successfully verified"
    )
    private String reason;
}

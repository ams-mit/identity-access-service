package lk.ac.kelaniya.ams.identity_access_service.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response payload returned upon administrative user account creation.
 * Contains identifiers and account status, and explicitly excludes temporary credentials.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Response payload returned upon administrative user account creation")
public class AdminCreateUserResponse {

    @Schema(description = "Unique user identifier", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
    private UUID userId;

    @Schema(description = "User email address", example = "jane.doe@example.com")
    private String email;

    @Schema(description = "Initial account lifecycle status (ACTIVE for admin-created users)", example = "ACTIVE")
    private AccountStatus accountStatus;

    @Schema(description = "Indicates whether the user must change their temporary password upon initial login", example = "true")
    private boolean mustChangePassword;
}

package lk.ac.kelaniya.ams.identity_access_service.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Summary representation of a user account for administrative search and listing.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Summary representation of a user for administrative list and search")
public class AdminUserSummaryResponse {

    @Schema(description = "Unique user identifier", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
    private UUID userId;

    @Schema(description = "User email address", example = "john.doe@example.com")
    private String email;

    @Schema(description = "User's full name", example = "John Doe")
    private String fullName;

    @Schema(description = "User's first name", example = "John")
    private String firstName;

    @Schema(description = "User's last name", example = "Doe")
    private String lastName;

    @Schema(description = "Current account status", example = "PENDING_VERIFICATION")
    private AccountStatus accountStatus;

    @Schema(
            description = "Role requested by user during registration. Inspected by administrators during review.",
            example = "OWNER"
    )
    private String requestedRole;

    @Schema(description = "Assigned / granted system roles", example = "[\"OWNER\"]")
    @Builder.Default
    @JsonProperty("roles")
    private List<String> roles = new ArrayList<>();

    @Schema(description = "Alias for assigned/granted system roles", example = "[\"OWNER\"]")
    @JsonProperty("grantedRoles")
    public List<String> getGrantedRoles() {
        return roles;
    }
}

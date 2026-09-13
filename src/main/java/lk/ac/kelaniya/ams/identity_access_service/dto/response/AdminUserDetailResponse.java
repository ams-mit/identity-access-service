package lk.ac.kelaniya.ams.identity_access_service.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Detailed representation of a user account for administrative inspection and review.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Detailed representation of a user for administrative inspection")
public class AdminUserDetailResponse {

    @Schema(description = "Unique user identifier", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
    private UUID userId;

    @Schema(description = "Username credential identifier", example = "john_doe")
    private String username;

    @Schema(description = "User email address", example = "john.doe@example.com")
    private String email;

    @Schema(description = "User's full name", example = "John Doe")
    private String fullName;

    @Schema(description = "User's first name", example = "John")
    private String firstName;

    @Schema(description = "User's last name", example = "Doe")
    private String lastName;

    @Schema(description = "User contact phone number", example = "+94771234567")
    private String phone;

    @Schema(description = "Current account lifecycle status", example = "PENDING_VERIFICATION")
    private AccountStatus accountStatus;

    @Schema(
            description = "Role requested by user during registration. Crucial for admin review workflow when deciding role assignments.",
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

    @Schema(description = "Number of consecutive failed login attempts", example = "0")
    private int failedAttemptCount;

    @Schema(description = "Timestamp until which the account is temporarily locked due to failed attempts")
    private Instant lockedUntil;

    @Schema(description = "Whether the account is currently locked", example = "false")
    private boolean accountLocked;

    @Schema(description = "Account creation timestamp", example = "2026-09-13T10:00:00Z")
    private Instant createdAt;

    @Schema(description = "Account last update timestamp", example = "2026-09-13T10:00:00Z")
    private Instant updatedAt;
}

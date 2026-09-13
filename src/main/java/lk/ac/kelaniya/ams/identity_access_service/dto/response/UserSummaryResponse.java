package lk.ac.kelaniya.ams.identity_access_service.dto.response;

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
 * Detailed user profile summary response returned by GET /api/v1/users/me.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "User profile summary response containing current database state")
public class UserSummaryResponse {

    @Schema(description = "Unique user identifier", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
    private UUID userId;

    @Schema(description = "User email address", example = "john.doe@example.com")
    private String email;

    @Schema(description = "User first name", example = "John")
    private String firstName;

    @Schema(description = "User last name", example = "Doe")
    private String lastName;

    @Schema(description = "Current account status", example = "ACTIVE")
    private AccountStatus accountStatus;

    @Schema(description = "Assigned user roles", example = "[\"ROLE_STUDENT\"]")
    @Builder.Default
    private List<String> roles = new ArrayList<>();

    @Schema(description = "Advisory role requested during registration for administrative review", example = "OWNER")
    private String requestedRole;
}

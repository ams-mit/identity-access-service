package lk.ac.kelaniya.ams.identity_access_service.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response payload returned upon successful user registration.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Registration response payload containing created user details")
public class RegisterResponse {

    @Schema(description = "Unique user identifier", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
    private UUID userId;

    @Schema(description = "Registered email address", example = "john.doe@example.com")
    private String email;

    @Schema(description = "Initial account verification status", example = "PENDING_VERIFICATION")
    private AccountStatus accountStatus;
}

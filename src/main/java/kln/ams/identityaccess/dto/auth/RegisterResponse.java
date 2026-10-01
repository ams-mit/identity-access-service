package kln.ams.identityaccess.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Registration response payload (AUTH-004).
 * Never contains passwords or password hashes.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Self-registration response payload containing created user summary")
public class RegisterResponse {

    @Schema(description = "User unique identifier", example = "7c5c9f4d-4df2-4d8e-9c7f-4d7e5e7a4c11")
    private UUID id;

    @Schema(description = "Account username", example = "john.perera@example.com")
    private String username;

    @Schema(description = "Account email", example = "john.perera@example.com")
    private String email;

    @Schema(description = "First name", example = "John")
    private String firstName;

    @Schema(description = "Last name", example = "Perera")
    private String lastName;

    @Schema(description = "Phone number", example = "+94771234567")
    private String phone;

    @Schema(description = "Initial account status", example = "INACTIVE")
    private String status;

    @Schema(description = "Assigned default canonical role", example = "[\"TENANT_RESIDENT\"]")
    private List<String> roles;

    @Schema(description = "Account registration timestamp")
    private Instant createdAt;
}

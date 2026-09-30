package kln.ams.identityaccess.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Identity user account response representation")
public class UserResponse {

    @Schema(description = "User unique identifier", example = "7c5c9f4d-4df2-4d8e-9c7f-4d7e5e7a4c11")
    private UUID id;

    @Schema(description = "Account username", example = "john@example.com")
    private String username;

    @Schema(description = "Account email", example = "john@example.com")
    private String email;

    @Schema(description = "First name", example = "John")
    private String firstName;

    @Schema(description = "Last name", example = "Perera")
    private String lastName;

    @Schema(description = "Phone number", example = "0771234567")
    private String phone;

    @Schema(description = "Account status", example = "ACTIVE")
    private String status;

    @Schema(description = "Assigned canonical roles", example = "[\"TENANT_RESIDENT\"]")
    private List<String> roles;

    @Schema(description = "Account creation timestamp")
    private Instant createdAt;

    @Schema(description = "Account last update timestamp")
    private Instant updatedAt;
}

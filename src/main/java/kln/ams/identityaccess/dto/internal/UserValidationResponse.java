package kln.ams.identityaccess.dto.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Internal user validation response")
public class UserValidationResponse {

    @Schema(description = "User unique identifier", example = "7c5c9f4d-4df2-4d8e-9c7f-4d7e5e7a4c11")
    private UUID userId;

    @Schema(description = "Indicates whether the user exists in identity access", example = "true")
    private boolean exists;

    @Schema(description = "Indicates whether the user account is active", example = "true")
    private boolean active;

    @Schema(description = "Assigned canonical roles", example = "[\"TENANT_RESIDENT\"]")
    private List<String> roles;

    @Schema(description = "Indicates whether the requested role matches user roles", example = "true")
    private Boolean roleMatches;
}

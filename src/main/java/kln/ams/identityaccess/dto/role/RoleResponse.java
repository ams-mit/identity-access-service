package kln.ams.identityaccess.dto.role;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Role representation response")
public class RoleResponse {

    @Schema(description = "Role unique identifier", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "Role name", example = "TENANT_RESIDENT")
    private String name;

    @Schema(description = "Role description", example = "Authenticated tenant/resident role")
    private String description;

    @Schema(description = "Role active flag", example = "true")
    private boolean active;
}

package kln.ams.identityaccess.dto.permission;

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
@Schema(description = "Permission definition representation")
public class PermissionResponse {

    @Schema(description = "Permission unique identifier", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "Permission code", example = "USER_MANAGE")
    private String code;

    @Schema(description = "Permission description", example = "Manage identity user accounts")
    private String description;

    @Schema(description = "Permission active flag", example = "true")
    private boolean active;
}

package kln.ams.identityaccess.dto.permission;

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
@Schema(description = "Permissions assigned to a role response")
public class RolePermissionsResponse {

    @Schema(description = "Role unique identifier", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID roleId;

    @Schema(description = "Canonical role name", example = "SYSTEM_ADMINISTRATOR")
    private String roleName;

    @Schema(description = "List of assigned permissions")
    private List<PermissionResponse> permissions;
}

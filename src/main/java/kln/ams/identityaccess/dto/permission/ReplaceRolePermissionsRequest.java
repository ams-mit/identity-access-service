package kln.ams.identityaccess.dto.permission;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
@Schema(description = "Role permission replacement payload")
public class ReplaceRolePermissionsRequest {

    @NotNull(message = "Permission IDs list is required")
    @Schema(description = "List of permission UUIDs to assign to role", example = "[\"550e8400-e29b-41d4-a716-446655440000\"]")
    private List<UUID> permissionIds;
}

package kln.ams.identityaccess.dto.role;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Role update payload")
public class UpdateRoleRequest {

    @Schema(description = "Role name (cannot be modified for the 9 canonical platform roles)", example = "CUSTOM_ROLE")
    private String name;

    @Schema(description = "Updated role description", example = "Updated role description")
    private String description;

    public UpdateRoleRequest(String description) {
        this.description = description;
    }
}

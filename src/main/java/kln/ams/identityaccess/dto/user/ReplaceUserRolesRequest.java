package kln.ams.identityaccess.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "User role replacement payload")
public class ReplaceUserRolesRequest {

    @NotEmpty(message = "Roles list cannot be empty")
    @Schema(description = "Replacement canonical roles", example = "[\"TENANT_RESIDENT\"]")
    private List<String> roles;
}

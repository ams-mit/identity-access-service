package kln.ams.identityaccess.dto.user;

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
@Schema(description = "Assigned user roles response")
public class UserRolesResponse {

    @Schema(description = "User unique identifier", example = "7c5c9f4d-4df2-4d8e-9c7f-4d7e5e7a4c11")
    private UUID userId;

    @Schema(description = "List of assigned canonical roles", example = "[\"TENANT_RESIDENT\"]")
    private List<String> roles;
}

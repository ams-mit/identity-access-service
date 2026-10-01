package kln.ams.identityaccess.dto.auth;

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
@Schema(description = "User context returned upon successful login")
public class LoginUserDto {

    @Schema(description = "User UUID", example = "7c5c9f4d-4df2-4d8e-9c7f-4d7e5e7a4c11")
    private UUID id;

    @Schema(description = "Account username", example = "john@example.com")
    private String username;

    @Schema(description = "Assigned canonical roles", example = "[\"TENANT_RESIDENT\"]")
    private List<String> roles;

    @Schema(description = "Account status", example = "ACTIVE")
    private String status;
}

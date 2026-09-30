package kln.ams.identityaccess.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "User login credentials payload")
public class LoginRequest {

    @NotBlank(message = "Username is required")
    @Schema(description = "Account username or email", example = "john@example.com")
    private String username;

    @NotBlank(message = "Password is required")
    @Schema(description = "Account password", example = "Password123!")
    private String password;
}

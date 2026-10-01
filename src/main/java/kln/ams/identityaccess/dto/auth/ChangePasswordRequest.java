package kln.ams.identityaccess.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import kln.ams.identityaccess.validation.ValidPassword;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Request payload for authenticated user self-service password update (AUTH-007).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"currentPassword", "newPassword", "confirmNewPassword"})
@Schema(description = "Self-service password update request payload")
public class ChangePasswordRequest {

    @NotBlank(message = "Current password is required")
    @Schema(description = "Current account password", example = "OldPassword123")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @ValidPassword
    @Schema(description = "New password (minimum 8 characters, at least one numeric digit)", example = "NewPassword123")
    private String newPassword;

    @NotBlank(message = "Password confirmation is required")
    @Schema(description = "Password confirmation matching new password", example = "NewPassword123")
    private String confirmNewPassword;
}

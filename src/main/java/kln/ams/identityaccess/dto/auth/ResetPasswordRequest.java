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
 * Request payload for completing password reset (AUTH-006).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"newPassword", "confirmNewPassword"})
@Schema(description = "Reset-password request payload")
public class ResetPasswordRequest {

    @NotBlank(message = "Reset token is required")
    @Schema(description = "Cryptographically secure password reset token", example = "aB3_dE9-xY2...")
    private String resetToken;

    @NotBlank(message = "New password is required")
    @ValidPassword
    @Schema(description = "New password (minimum 8 characters, at least one numeric digit)", example = "NewPassword123!")
    private String newPassword;

    @NotBlank(message = "Password confirmation is required")
    @Schema(description = "Password confirmation matching new password", example = "NewPassword123!")
    private String confirmNewPassword;
}

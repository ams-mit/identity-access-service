package lk.ac.kelaniya.ams.identity_access_service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Request payload for resetting user password using a password reset token.
 * Reuses the platform password complexity policy established in registration and change-password.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"newPassword", "confirmNewPassword"})
@Schema(description = "Request payload for resetting password using a reset token")
public class ResetPasswordRequest {

    @NotBlank(message = "Reset token is required")
    @Schema(description = "Cryptographically secure password reset token", example = "aB3_dE9-xY2...")
    private String resetToken;

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters long")
    @Pattern(
            regexp = "^(?=.*[0-9]).{8,}$",
            message = "Password must be at least 8 characters long and contain at least one numeric digit"
    )
    @Schema(description = "New password (minimum 8 characters, at least one digit)", example = "NewP@ssword123")
    private String newPassword;

    @NotBlank(message = "Password confirmation is required")
    @Schema(description = "Password confirmation matching the new password field", example = "NewP@ssword123")
    private String confirmNewPassword;
}

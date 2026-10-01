package kln.ams.identityaccess.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kln.ams.identityaccess.validation.ValidPassword;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Self-registration request payload (AUTH-004).
 * Role is not accepted from the client; the canonical TENANT_RESIDENT role is assigned automatically.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"password", "confirmPassword"})
@Schema(description = "User self-registration request payload")
public class RegisterRequest {

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must not exceed 100 characters")
    @Schema(description = "User first name", example = "John")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    @Schema(description = "User last name", example = "Perera")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    @Size(max = 150, message = "Email must not exceed 150 characters")
    @Schema(description = "Unique email address used for login and notifications", example = "john.perera@example.com")
    private String email;

    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    @Schema(description = "Contact phone number", example = "+94771234567")
    private String phone;

    @NotBlank(message = "Password is required")
    @ValidPassword
    @Schema(description = "Account password (minimum 8 characters, at least one numeric digit)", example = "Password123")
    private String password;

    @NotBlank(message = "Password confirmation is required")
    @Schema(description = "Password confirmation matching the password field", example = "Password123")
    private String confirmPassword;
}

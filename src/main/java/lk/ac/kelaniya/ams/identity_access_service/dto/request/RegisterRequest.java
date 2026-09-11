package lk.ac.kelaniya.ams.identity_access_service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Registration request payload.
 * Role or privileged fields are intentionally omitted to prevent client-supplied privilege assignment.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "User registration request payload")
public class RegisterRequest {

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must not exceed 100 characters")
    @Schema(description = "User first name", example = "John")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    @Schema(description = "User last name", example = "Doe")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    @Size(max = 150, message = "Email must not exceed 150 characters")
    @Schema(description = "Unique email address used for login", example = "john.doe@example.com")
    private String email;

    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    @Schema(description = "Contact phone number", example = "+94771234567")
    private String phone;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters long")
    @Pattern(
            regexp = "^(?=.*[0-9]).{8,}$",
            message = "Password must be at least 8 characters long and contain at least one numeric digit"
    )
    @Schema(description = "Account password (minimum 8 characters, at least one digit)", example = "P@ssword123")
    private String password;

    @NotBlank(message = "Password confirmation is required")
    @Schema(description = "Password confirmation matching the password field", example = "P@ssword123")
    private String confirmPassword;
}

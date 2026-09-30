package kln.ams.identityaccess.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "User account creation payload")
public class CreateUserRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 150, message = "Username must be between 3 and 150 characters")
    @Schema(description = "Account username", example = "john@example.com")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    @Schema(description = "Account email address", example = "john@example.com")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    @Schema(description = "Account password", example = "Password123!")
    private String password;

    @NotBlank(message = "First name is required")
    @Schema(description = "First name", example = "John")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Schema(description = "Last name", example = "Perera")
    private String lastName;

    @Schema(description = "Phone number", example = "0771234567")
    private String phone;

    @NotEmpty(message = "At least one canonical role must be specified")
    @Schema(description = "List of canonical roles to assign", example = "[\"TENANT_RESIDENT\"]")
    private List<String> roles;
}

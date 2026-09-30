package kln.ams.identityaccess.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Account field update payload")
public class UpdateUserRequest {

    @Email(message = "Email must be a valid email address")
    @Schema(description = "New email address", example = "new.email@example.com")
    private String email;

    @Schema(description = "Updated first name", example = "John")
    private String firstName;

    @Schema(description = "Updated last name", example = "Perera")
    private String lastName;

    @Schema(description = "Updated phone number", example = "0771234567")
    private String phone;
}

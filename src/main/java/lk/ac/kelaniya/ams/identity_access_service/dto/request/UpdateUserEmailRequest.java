package lk.ac.kelaniya.ams.identity_access_service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for updating a user's email address by an authorized internal service.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request payload for updating a user's email address by an authorized internal service")
public class UpdateUserEmailRequest {

    @NotBlank(message = "New email address is required")
    @Email(message = "New email must be a valid email address")
    @Size(max = 150, message = "Email must not exceed 150 characters")
    @Schema(description = "New email address for the user", example = "resident.new@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String newEmail;
}

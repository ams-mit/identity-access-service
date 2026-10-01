package kln.ams.identityaccess.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Standard informational message payload.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard informational message payload")
public class MessageResponse {

    @Schema(description = "Informational status message", example = "If an account is associated with this email, instructions will be provided.")
    private String message;
}

package lk.ac.kelaniya.ams.identity_access_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Standard informational message response payload.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard informational message response")
public class MessageResponse {

    @Schema(
            description = "Informational message description",
            example = "If an account is associated with this email, instructions will be provided."
    )
    private String message;
}

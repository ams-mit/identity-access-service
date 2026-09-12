package lk.ac.kelaniya.ams.identity_access_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Standard API error envelope formatted as:
 * {
 *   "error": {
 *     "code": "...",
 *     "message": "..."
 *   }
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard error envelope")
public class ErrorResponse {

    @Schema(description = "Error details")
    private ErrorBody error;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Error payload containing code and message")
    public static class ErrorBody {

        @Schema(description = "Error code identifier", example = "VALIDATION_ERROR")
        private String code;

        @Schema(description = "Human-readable error description", example = "Email already in use")
        private String message;
    }

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(new ErrorBody(code, message));
    }
}

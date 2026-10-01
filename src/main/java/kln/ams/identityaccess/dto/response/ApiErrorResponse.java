package kln.ams.identityaccess.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.MDC;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard Project A error response envelope")
public class ApiErrorResponse {

    @Schema(description = "Operation success flag", example = "false")
    @Builder.Default
    private boolean success = false;

    @Schema(description = "Human-readable error explanation", example = "Request validation failed")
    private String message;

    @Schema(description = "Structured error object")
    private ErrorDetail error;

    @Schema(description = "ISO-8601 response timestamp", example = "2026-09-30T12:00:00Z")
    private String timestamp;

    @Schema(description = "Request trace identifier", example = "7f83a9b2-4df2-4d8e-9c7f-4d7e5e7a4c11")
    private String requestId;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Machine-readable error detail")
    public static class ErrorDetail {

        @Schema(description = "Stable machine-readable error code", example = "VALIDATION_ERROR")
        private String code;

        @Schema(description = "Optional structured details such as validation field errors")
        private Object details;
    }

    public static ApiErrorResponse of(String code, String message, Object details) {
        String currentRequestId = MDC.get("requestId");
        return ApiErrorResponse.builder()
                .success(false)
                .message(message)
                .error(new ErrorDetail(code, details))
                .timestamp(DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
                .requestId(currentRequestId != null ? currentRequestId : "")
                .build();
    }

    public static ApiErrorResponse of(String code, String message) {
        return of(code, message, null);
    }
}

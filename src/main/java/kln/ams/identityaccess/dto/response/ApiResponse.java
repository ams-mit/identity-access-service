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
@Schema(description = "Standard Project A successful API response envelope")
public class ApiResponse<T> {

    @Schema(description = "Operation success flag", example = "true")
    @Builder.Default
    private boolean success = true;

    @Schema(description = "Human-readable operation message", example = "Operation completed successfully")
    private String message;

    @Schema(description = "Response payload")
    private T data;

    @Schema(description = "ISO-8601 response timestamp", example = "2026-09-30T12:00:00Z")
    private String timestamp;

    @Schema(description = "Request trace identifier", example = "7f83a9b2-4df2-4d8e-9c7f-4d7e5e7a4c11")
    private String requestId;

    public static <T> ApiResponse<T> ok(String message, T data) {
        String currentRequestId = MDC.get("requestId");
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
                .requestId(currentRequestId != null ? currentRequestId : "")
                .build();
    }

    public static <T> ApiResponse<T> of(String message, T data, String requestId) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
                .requestId(requestId != null ? requestId : "")
                .build();
    }
}

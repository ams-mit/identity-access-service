package lk.ac.kelaniya.ams.identity_access_service.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;

/**
 * Standard API response envelope wrapping all successful responses containing a body.
 *
 * @param <T> Payload data type
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Standard API response envelope")
public class ApiResponse<T> {

    @Schema(description = "Response data payload")
    private T data;

    @Schema(description = "Response metadata (e.g. pagination or additional context)")
    @Builder.Default
    private Object meta = Collections.emptyMap();

    public static <T> ApiResponse<T> of(T data) {
        return ApiResponse.<T>builder()
                .data(data)
                .meta(Collections.emptyMap())
                .build();
    }

    public static <T> ApiResponse<T> of(T data, Object meta) {
        return ApiResponse.<T>builder()
                .data(data)
                .meta(meta != null ? meta : Collections.emptyMap())
                .build();
    }
}

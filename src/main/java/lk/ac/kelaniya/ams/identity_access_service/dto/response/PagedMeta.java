package lk.ac.kelaniya.ams.identity_access_service.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Standard pagination metadata structure.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Pagination metadata")
public class PagedMeta {

    @Schema(description = "Zero-based page index", example = "0")
    private int page;

    @Schema(description = "Page size (elements per page)", example = "20")
    private int size;

    @Schema(description = "Total number of elements matching query", example = "42")
    private long totalElements;
}

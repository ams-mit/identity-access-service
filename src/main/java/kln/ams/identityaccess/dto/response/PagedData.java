package kln.ams.identityaccess.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Paginated collection container")
public class PagedData<T> {

    @Schema(description = "Page items")
    private List<T> items;

    @Schema(description = "Zero-indexed current page", example = "0")
    private int page;

    @Schema(description = "Page size", example = "20")
    private int size;

    @Schema(description = "Total elements across all pages", example = "42")
    private long totalElements;

    @Schema(description = "Total number of pages", example = "3")
    private int totalPages;
}

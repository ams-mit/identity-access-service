package kln.ams.identityaccess.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/**
 * Reusable paginated response envelope conforming to Project A Global API Standard:
 * {
 *   "success": true,
 *   "message": "...",
 *   "data": [ ...items ],
 *   "pagination": { "page", "size", "totalElements", "totalPages", "hasNext", "hasPrevious" },
 *   "timestamp": "...",
 *   "requestId": "..."
 * }
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Reusable paginated response envelope for list endpoints")
public class PaginatedApiResponse<T> extends ApiResponse<List<T>> {

    public PaginatedApiResponse() {
        super();
    }

    public static <T> PaginatedApiResponse<T> of(String message, List<T> items, PaginationMetadata pagination) {
        ApiResponse<List<T>> base = ApiResponse.paginated(message, items, pagination);
        PaginatedApiResponse<T> response = new PaginatedApiResponse<>();
        response.setSuccess(base.isSuccess());
        response.setMessage(base.getMessage());
        response.setData(base.getData());
        response.setPagination(base.getPagination());
        response.setTimestamp(base.getTimestamp());
        response.setRequestId(base.getRequestId());
        return response;
    }
}

package lk.ac.kelaniya.ams.identity_access_service.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response payload representing an AMS system role.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Role details response payload")
public class RoleResponse {

    @Schema(description = "Unique role identifier", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
    private UUID id;

    @Schema(description = "Standard AMS role name", example = "SYSTEM_ADMINISTRATOR")
    private String name;

    @Schema(description = "Description of the role and its privileges", example = "Full administrative access")
    private String description;
}

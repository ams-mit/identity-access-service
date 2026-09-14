package lk.ac.kelaniya.ams.identity_access_service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for administrative role assignment to a user.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request payload for assigning a staff or system role to a user")
public class AssignRoleRequest {

    @NotBlank(message = "Role name is required")
    @Schema(
            description = "Standard AMS role name to grant to the user",
            example = "FINANCE_OFFICER",
            allowableValues = {
                    "SYSTEM_ADMINISTRATOR",
                    "APARTMENT_MANAGER",
                    "OWNER",
                    "TENANT_RESIDENT",
                    "FINANCE_OFFICER",
                    "MAINTENANCE_COORDINATOR",
                    "TECHNICIAN",
                    "SECURITY_OFFICER"
            }
    )
    private String role;
}

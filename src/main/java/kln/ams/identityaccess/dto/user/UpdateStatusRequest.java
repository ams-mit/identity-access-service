package kln.ams.identityaccess.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import kln.ams.identityaccess.entity.AccountStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Account status transition payload")
public class UpdateStatusRequest {

    @NotNull(message = "Account status is required")
    @Schema(description = "Target account status (ACTIVE, INACTIVE, SUSPENDED)", example = "ACTIVE")
    private AccountStatus status;
}

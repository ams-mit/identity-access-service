package kln.ams.identityaccess.dto.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Internal user account status response")
public class UserStatusResponse {

    @Schema(description = "User unique identifier", example = "7c5c9f4d-4df2-4d8e-9c7f-4d7e5e7a4c11")
    private UUID userId;

    @Schema(description = "Account status identifier", example = "ACTIVE")
    private String status;

    @Schema(description = "Indicates whether the user account is active and able to authenticate", example = "true")
    private boolean active;
}

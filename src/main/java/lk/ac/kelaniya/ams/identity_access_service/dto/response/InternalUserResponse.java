package lk.ac.kelaniya.ams.identity_access_service.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Minimal user authorization payload returned exclusively to internal microservices.
 * Excludes all sensitive PII and credential data (no email, password hash, phone, or name).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Internal user authorization details containing only fields required for downstream authorization decisions")
public class InternalUserResponse {

    @Schema(description = "Unique user identifier", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
    private UUID userId;

    @Schema(description = "Current account lifecycle status", example = "ACTIVE")
    private AccountStatus accountStatus;

    @Schema(description = "List of assigned AMS role names", example = "[\"TENANT_RESIDENT\"]")
    private List<String> roles;
}

package lk.ac.kelaniya.ams.identity_access_service.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Response payload returned upon successful user authentication.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Authentication response payload containing access token and user information")
public class LoginResponse {

    @Schema(description = "RS256-signed JWT access token", example = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6ImFtc...}")
    private String accessToken;

    @Schema(description = "Token lifetime in seconds", example = "1800")
    private long expiresIn;

    @Schema(description = "Authenticated user details")
    private UserSummary user;

    /**
     * Nested summary of the authenticated user.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "User details associated with the issued access token")
    public static class UserSummary {

        @Schema(description = "Unique user identifier", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
        private UUID userId;

        @Schema(description = "User email address", example = "john.doe@example.com")
        private String email;

        @Schema(description = "Assigned roles", example = "[]")
        @Builder.Default
        private List<String> roles = new ArrayList<>();
    }
}

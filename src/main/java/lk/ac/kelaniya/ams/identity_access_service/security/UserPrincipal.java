package lk.ac.kelaniya.ams.identity_access_service.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * Principal object representing the authenticated user extracted from the verified JWT.
 */
@Getter
@Builder
@AllArgsConstructor
public class UserPrincipal implements Principal, Serializable {

    private final UUID userId;
    private final String email;
    private final List<String> roles;

    @Override
    public String getName() {
        return userId != null ? userId.toString() : null;
    }
}

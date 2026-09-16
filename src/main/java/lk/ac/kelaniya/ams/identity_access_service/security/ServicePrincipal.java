package lk.ac.kelaniya.ams.identity_access_service.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;
import java.security.Principal;

/**
 * Principal object representing an authenticated microservice caller extracted from a validated service JWT.
 */
@Getter
@Builder
@AllArgsConstructor
public class ServicePrincipal implements Principal, Serializable {

    private final String serviceName;
    private final String tokenType;

    @Override
    public String getName() {
        return serviceName;
    }
}

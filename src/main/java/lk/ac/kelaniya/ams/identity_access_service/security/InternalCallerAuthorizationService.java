package lk.ac.kelaniya.ams.identity_access_service.security;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Service enforcing caller-service authorization on internal endpoints (Rule 10).
 * Verifies that the calling microservice's identity (ServicePrincipal.getServiceName())
 * is explicitly allow-listed for a specific internal endpoint.
 */
@Slf4j
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "internal-api")
public class InternalCallerAuthorizationService {

    public static final String USER_VALIDATION_ENDPOINT = "user-validation";

    /**
     * Map of endpointKey -> list of allowed caller service names.
     */
    private Map<String, List<String>> allowedCallers = new HashMap<>();

    /**
     * Fallback default allow-list for user-validation endpoint based on cross-team dependencies.
     */
    public static final List<String> DEFAULT_USER_VALIDATION_CALLERS = List.of(
            "resident-management-service",
            "lease-occupancy-service",
            "billing-payment-service",
            "utility-charge-service",
            "operations-service",
            "community-service",
            "property-unit-service"
    );

    /**
     * Checks if the specified caller service is allow-listed for the given endpoint key.
     *
     * @param callerServiceName the calling microservice name (ServicePrincipal.getServiceName())
     * @param endpointKey       the internal endpoint identifier (e.g. "user-validation")
     * @return true if caller is authorized, false otherwise
     */
    public boolean isAllowed(String callerServiceName, String endpointKey) {
        if (callerServiceName == null || callerServiceName.isBlank() || endpointKey == null || endpointKey.isBlank()) {
            return false;
        }

        List<String> allowed = allowedCallers.get(endpointKey);
        if (allowed == null || allowed.isEmpty()) {
            if (USER_VALIDATION_ENDPOINT.equals(endpointKey)) {
                allowed = DEFAULT_USER_VALIDATION_CALLERS;
            } else {
                allowed = Collections.emptyList();
            }
        }

        String normalizedCaller = callerServiceName.trim().toLowerCase(Locale.ROOT);
        return allowed.stream()
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .anyMatch(normalizedCaller::equals);
    }
}

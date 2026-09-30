package kln.ams.identityaccess.security;

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

@Slf4j
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "internal-api")
public class InternalCallerAuthorizationService {

    public static final String USER_VALIDATION_ENDPOINT = "user-validation";
    public static final String USER_STATUS_ENDPOINT = "user-status";

    private Map<String, List<String>> allowedCallers = new HashMap<>();

    public static final List<String> IAM_INT_001_ALLOWED_CONSUMERS = List.of(
            "resident-management-service",
            "property-unit-service",
            "lease-occupancy-service",
            "billing-payment-service",
            "operations-service",
            "community-service"
    );

    public static final List<String> IAM_INT_002_ALLOWED_CONSUMERS = List.of(
            "resident-management-service",
            "billing-payment-service",
            "operations-service",
            "community-service"
    );

    public boolean isAllowed(String callerServiceName, String endpointKey) {
        if (callerServiceName == null || callerServiceName.isBlank()) {
            return false;
        }

        List<String> allowed = allowedCallers.get(endpointKey);
        if (allowed == null || allowed.isEmpty()) {
            if (USER_STATUS_ENDPOINT.equalsIgnoreCase(endpointKey) || "IAM-INT-002".equalsIgnoreCase(endpointKey)) {
                allowed = IAM_INT_002_ALLOWED_CONSUMERS;
            } else {
                allowed = IAM_INT_001_ALLOWED_CONSUMERS;
            }
        }

        String normalizedCaller = callerServiceName.trim().toLowerCase(Locale.ROOT);
        return allowed.stream()
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .anyMatch(normalizedCaller::equals);
    }
}

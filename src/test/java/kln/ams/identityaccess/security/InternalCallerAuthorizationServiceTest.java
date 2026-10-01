package kln.ams.identityaccess.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InternalCallerAuthorizationServiceTest {

    private InternalCallerAuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        authorizationService = new InternalCallerAuthorizationService();
    }

    @Test
    void isAllowed_UserValidation_AllowsRegistryListedConsumers() {
        String endpoint = InternalCallerAuthorizationService.USER_VALIDATION_ENDPOINT;

        // Registry consumers for IAM-INT-001:
        assertThat(authorizationService.isAllowed("resident-management-service", endpoint)).isTrue();
        assertThat(authorizationService.isAllowed("property-unit-service", endpoint)).isTrue();
        assertThat(authorizationService.isAllowed("lease-occupancy-service", endpoint)).isTrue();
        assertThat(authorizationService.isAllowed("billing-payment-service", endpoint)).isTrue();
        assertThat(authorizationService.isAllowed("operations-service", endpoint)).isTrue();
        assertThat(authorizationService.isAllowed("community-service", endpoint)).isTrue();

        // Unlisted consumers:
        assertThat(authorizationService.isAllowed("untrusted-service", endpoint)).isFalse();
        assertThat(authorizationService.isAllowed("", endpoint)).isFalse();
        assertThat(authorizationService.isAllowed(null, endpoint)).isFalse();
    }

    @Test
    void isAllowed_UserStatus_AllowsRegistryListedConsumers() {
        String endpoint = InternalCallerAuthorizationService.USER_STATUS_ENDPOINT;

        // Registry consumers for IAM-INT-002:
        assertThat(authorizationService.isAllowed("resident-management-service", endpoint)).isTrue();
        assertThat(authorizationService.isAllowed("billing-payment-service", endpoint)).isTrue();
        assertThat(authorizationService.isAllowed("operations-service", endpoint)).isTrue();
        assertThat(authorizationService.isAllowed("community-service", endpoint)).isTrue();

        // Unlisted consumers (e.g., property-unit-service is only consumer of validation, not status):
        assertThat(authorizationService.isAllowed("property-unit-service", endpoint)).isFalse();
        assertThat(authorizationService.isAllowed("untrusted-service", endpoint)).isFalse();
        assertThat(authorizationService.isAllowed(null, endpoint)).isFalse();
    }
}

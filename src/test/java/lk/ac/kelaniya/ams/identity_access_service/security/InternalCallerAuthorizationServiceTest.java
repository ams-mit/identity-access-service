package lk.ac.kelaniya.ams.identity_access_service.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InternalCallerAuthorizationServiceTest {

    private InternalCallerAuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        authorizationService = new InternalCallerAuthorizationService();
    }

    @Test
    @DisplayName("isAllowed returns true for allow-listed service")
    void testIsAllowed_allowListedService_returnsTrue() {
        authorizationService.setAllowedCallers(Map.of(
                "user-validation", List.of("resident-management-service", "lease-occupancy-service")
        ));

        assertThat(authorizationService.isAllowed("resident-management-service", "user-validation")).isTrue();
        assertThat(authorizationService.isAllowed("RESIDENT-MANAGEMENT-SERVICE", "user-validation")).isTrue();
        assertThat(authorizationService.isAllowed("  lease-occupancy-service  ", "user-validation")).isTrue();
    }

    @Test
    @DisplayName("isAllowed returns false for non-allow-listed service")
    void testIsAllowed_nonAllowListedService_returnsFalse() {
        authorizationService.setAllowedCallers(Map.of(
                "user-validation", List.of("resident-management-service")
        ));

        assertThat(authorizationService.isAllowed("unauthorized-service", "user-validation")).isFalse();
        assertThat(authorizationService.isAllowed("billing-payment-service", "user-validation")).isFalse();
    }

    @Test
    @DisplayName("isAllowed returns false for null, blank caller or endpoint")
    void testIsAllowed_nullOrBlankInputs_returnsFalse() {
        authorizationService.setAllowedCallers(Map.of(
                "user-validation", List.of("resident-management-service")
        ));

        assertThat(authorizationService.isAllowed(null, "user-validation")).isFalse();
        assertThat(authorizationService.isAllowed("  ", "user-validation")).isFalse();
        assertThat(authorizationService.isAllowed("resident-management-service", null)).isFalse();
        assertThat(authorizationService.isAllowed("resident-management-service", "   ")).isFalse();
        assertThat(authorizationService.isAllowed("resident-management-service", "unknown-endpoint")).isFalse();
    }

    @Test
    @DisplayName("isAllowed falls back to default allow-list when unconfigured for user-validation")
    void testIsAllowed_defaultFallbackForUserValidation() {
        assertThat(authorizationService.isAllowed("resident-management-service", "user-validation")).isTrue();
        assertThat(authorizationService.isAllowed("billing-payment-service", "user-validation")).isTrue();
        assertThat(authorizationService.isAllowed("operations-service", "user-validation")).isTrue();
        assertThat(authorizationService.isAllowed("unauthorized-analytics-service", "user-validation")).isFalse();
    }
}

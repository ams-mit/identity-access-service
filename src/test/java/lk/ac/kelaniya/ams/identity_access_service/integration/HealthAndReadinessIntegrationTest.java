package lk.ac.kelaniya.ams.identity_access_service.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests verifying Spring Boot Actuator health and readiness probes
 * with real MySQL connectivity via Testcontainers and security exposure boundaries.
 */
class HealthAndReadinessIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("GET /actuator/health/liveness returns HTTP 200 with UP status unauthenticated")
    void testLivenessProbeReturns200Unauthenticated() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/actuator/health/liveness",
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    @DisplayName("GET /actuator/health/readiness returns HTTP 200 with UP status when real MySQL DB is reachable")
    void testReadinessProbeReturns200WhenDbReachable() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/actuator/health/readiness",
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    @DisplayName("GET /actuator/health returns HTTP 200 without leaking internal component details")
    void testGeneralHealthReturns200WithoutDetails() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/actuator/health",
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).contains("\"status\":\"UP\"");

        // Confirm safe-by-default exposure: show-details=never must not leak database URLs, credentials, or internal paths
        assertThat(response.getBody()).doesNotContain("jdbc:");
        assertThat(response.getBody()).doesNotContain("identity_user");
        assertThat(response.getBody()).doesNotContain("identity_db");
    }

    @Test
    @DisplayName("Unexposed actuator endpoints (/actuator/env, /actuator/beans, /actuator/configprops) are not accessible")
    void testUnexposedActuatorEndpointsAreNotAccessible() {
        String[] sensitiveEndpoints = {"/actuator/env", "/actuator/beans", "/actuator/configprops"};

        for (String endpoint : sensitiveEndpoints) {
            ResponseEntity<String> response = restTemplate.getForEntity(endpoint, String.class);

            // Must be either 404 (not exposed by Actuator) or 401 (blocked by Spring Security)
            assertThat(response.getStatusCode())
                    .as("Sensitive endpoint %s must not be accessible (got %s)", endpoint, response.getStatusCode())
                    .isIn(HttpStatus.NOT_FOUND, HttpStatus.UNAUTHORIZED);
            assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.OK);
        }
    }
}

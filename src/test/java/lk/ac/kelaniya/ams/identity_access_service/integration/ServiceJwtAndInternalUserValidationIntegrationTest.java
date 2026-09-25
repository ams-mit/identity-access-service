package lk.ac.kelaniya.ams.identity_access_service.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.InternalUserResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lk.ac.kelaniya.ams.identity_access_service.entity.User;
import lk.ac.kelaniya.ams.identity_access_service.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for Service JWT issuance and internal user-validation endpoint.
 * Validates strict isolation between internal service caller privileges and human user privileges.
 */
class ServiceJwtAndInternalUserValidationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private lk.ac.kelaniya.ams.identity_access_service.security.RsaKeyProvider rsaKeyProvider;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Helper simulating an external service's self-signed Service JWT per JWT Standard Rules 2 & 5.
     */
    private String createForeignServiceToken(String serviceName) {
        java.time.Instant now = java.time.Instant.now();
        return io.jsonwebtoken.Jwts.builder()
                .subject(serviceName)
                .claim("type", "service")
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plusSeconds(300)))
                .signWith(GATEWAY_KEY_PAIR.getPrivate(), io.jsonwebtoken.Jwts.SIG.RS256)
                .compact();
    }

    @Test
    @DisplayName("GET /internal/v1/users/{userId} with valid service token returns 200 and minimal authorization payload")
    void testInternalUserEndpoint_validServiceToken_returns200AndMinimalFields() throws Exception {
        User user = seedUserWithRole("resident.validation@ams.lk", "SecurePass1!", "TENANT_RESIDENT", AccountStatus.ACTIVE);

        String serviceToken = createForeignServiceToken("resident-management-service");
        HttpEntity<Void> requestEntity = new HttpEntity<>(authHeaders(serviceToken));

        ResponseEntity<String> rawResponse = restTemplate.exchange(
                "/internal/v1/users/" + user.getId(),
                HttpMethod.GET,
                requestEntity,
                String.class
        );

        assertThat(rawResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rawResponse.getBody()).isNotBlank();

        InternalUserResponse response = objectMapper.readValue(rawResponse.getBody(), InternalUserResponse.class);
        assertThat(response.getUserId()).isEqualTo(user.getId());
        assertThat(response.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(response.getRoles()).containsExactly("TENANT_RESIDENT");

        // Explicitly assert zero PII leakage in the serialized JSON
        JsonNode jsonNode = objectMapper.readTree(rawResponse.getBody());
        assertThat(jsonNode.has("email")).isFalse();
        assertThat(jsonNode.has("passwordHash")).isFalse();
        assertThat(jsonNode.has("firstName")).isFalse();
        assertThat(jsonNode.has("lastName")).isFalse();
        assertThat(jsonNode.has("phone")).isFalse();
    }

    @Test
    @DisplayName("Critical Security Test: Valid SYSTEM_ADMINISTRATOR user token is REJECTED (403) from internal endpoint")
    void testInternalUserEndpoint_systemAdminUserToken_returns403Forbidden() {
        seedAdminUser("sysadmin.isolated@ams.lk", "AdminPass123!");
        String adminToken = loginAndGetToken("sysadmin.isolated@ams.lk", "AdminPass123!");

        UUID randomTargetUserId = UUID.randomUUID();
        HttpEntity<Void> requestEntity = new HttpEntity<>(authHeaders(adminToken));

        ResponseEntity<String> response = restTemplate.exchange(
                "/internal/v1/users/" + randomTargetUserId,
                HttpMethod.GET,
                requestEntity,
                String.class
        );

        // Administrator privilege must NOT leak into internal service-to-service access
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Regular user token (TENANT_RESIDENT) is REJECTED (403) from internal endpoint")
    void testInternalUserEndpoint_regularUserToken_returns403Forbidden() {
        seedUserWithRole("resident.user@ams.lk", "ResidentPass123!", "TENANT_RESIDENT", AccountStatus.ACTIVE);
        String residentToken = loginAndGetToken("resident.user@ams.lk", "ResidentPass123!");

        UUID randomTargetUserId = UUID.randomUUID();
        HttpEntity<Void> requestEntity = new HttpEntity<>(authHeaders(residentToken));

        ResponseEntity<String> response = restTemplate.exchange(
                "/internal/v1/users/" + randomTargetUserId,
                HttpMethod.GET,
                requestEntity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Unauthenticated request to internal endpoint returns 401 UNAUTHORIZED with standard JSON error body (Rule 8)")
    void testInternalUserEndpoint_unauthenticated_returns401Unauthorized() throws Exception {
        UUID randomTargetUserId = UUID.randomUUID();
        HttpEntity<Void> requestEntity = new HttpEntity<>(authHeaders(null));

        ResponseEntity<String> response = restTemplate.exchange(
                "/internal/v1/users/" + randomTargetUserId,
                HttpMethod.GET,
                requestEntity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotBlank();
        JsonNode jsonNode = objectMapper.readTree(response.getBody());
        assertThat(jsonNode.has("error")).isTrue();
        assertThat(jsonNode.get("error").get("code").asText()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("Malformed or tampered token to internal endpoint returns 401 UNAUTHORIZED")
    void testInternalUserEndpoint_tamperedToken_returns401Unauthorized() {
        UUID randomTargetUserId = UUID.randomUUID();
        HttpEntity<Void> requestEntity = new HttpEntity<>(authHeaders("malformed.jwt.token"));

        ResponseEntity<String> response = restTemplate.exchange(
                "/internal/v1/users/" + randomTargetUserId,
                HttpMethod.GET,
                requestEntity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("GET /internal/v1/users/{userId} for non-existent user returns 404 NOT FOUND")
    void testInternalUserEndpoint_nonExistentUser_returns404NotFound() {
        String serviceToken = createForeignServiceToken("billing-payment-service");
        UUID nonExistentUserId = UUID.randomUUID();
        HttpEntity<Void> requestEntity = new HttpEntity<>(authHeaders(serviceToken));

        ResponseEntity<String> response = restTemplate.exchange(
                "/internal/v1/users/" + nonExistentUserId,
                HttpMethod.GET,
                requestEntity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Service token is denied from accessing user profile endpoint GET /api/v1/users/me")
    void testServiceToken_deniedFromUserProfileEndpoint() {
        String serviceToken = createForeignServiceToken("operations-service");
        HttpEntity<Void> requestEntity = new HttpEntity<>(authHeaders(serviceToken));

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/users/me",
                HttpMethod.GET,
                requestEntity,
                String.class
        );

        // Service token does not represent a human user principal
        assertThat(response.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Identity Access Service rejects minting service tokens on behalf of other microservices (Rules 2, 5, 12)")
    void testJwtService_rejectsMintingForOtherServices() {
        org.junit.jupiter.api.Assertions.assertThrows(
                lk.ac.kelaniya.ams.identity_access_service.exception.UntrustedServiceException.class,
                () -> jwtService.generateServiceToken("resident-management-service")
        );
    }

    @Test
    @DisplayName("Valid service token from NON-allow-listed service is REJECTED (403) from internal endpoint (Rule 10)")
    void testInternalUserEndpoint_nonAllowListedService_returns403Forbidden() throws Exception {
        User user = seedUserWithRole("resident.nonallowed@ams.lk", "SecurePass1!", "TENANT_RESIDENT", AccountStatus.ACTIVE);

        // identity-access-service is in TRUSTED_SERVICES (ROLE_SERVICE) but not on user-validation allow-list
        String serviceToken = createForeignServiceToken("identity-access-service");
        HttpEntity<Void> requestEntity = new HttpEntity<>(authHeaders(serviceToken));

        ResponseEntity<String> response = restTemplate.exchange(
                "/internal/v1/users/" + user.getId(),
                HttpMethod.GET,
                requestEntity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        JsonNode jsonNode = objectMapper.readTree(response.getBody());
        assertThat(jsonNode.has("error")).isTrue();
        assertThat(jsonNode.get("error").get("code").asText()).isEqualTo("FORBIDDEN");
    }

    @Test
    @DisplayName("Untrusted/unregistered service JWT is REJECTED with 401 UNAUTHORIZED and JSON body (Rule 8)")
    void testInternalUserEndpoint_untrustedService_returns401Unauthorized() throws Exception {
        User user = seedUserWithRole("resident.untrusted@ams.lk", "SecurePass1!", "TENANT_RESIDENT", AccountStatus.ACTIVE);

        String serviceToken = createForeignServiceToken("unknown-malicious-service");
        HttpEntity<Void> requestEntity = new HttpEntity<>(authHeaders(serviceToken));

        ResponseEntity<String> response = restTemplate.exchange(
                "/internal/v1/users/" + user.getId(),
                HttpMethod.GET,
                requestEntity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        JsonNode jsonNode = objectMapper.readTree(response.getBody());
        assertThat(jsonNode.has("error")).isTrue();
        assertThat(jsonNode.get("error").get("code").asText()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("Existing user flows remain completely unaffected by service token introduction")
    void testExistingUserFlows_unaffected() {
        seedUserWithRole("existing.user@ams.lk", "StandardPass1!", "TENANT_RESIDENT", AccountStatus.ACTIVE);
        String userToken = loginAndGetToken("existing.user@ams.lk", "StandardPass1!");

        HttpEntity<Void> requestEntity = new HttpEntity<>(authHeaders(userToken));
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/users/me",
                HttpMethod.GET,
                requestEntity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("existing.user@ams.lk");
    }

    @Test
    @DisplayName("Incoming token signed with Identity Access private key is REJECTED (401) because filter verifies only with Gateway public key")
    void testIncomingToken_signedWithIdentityAccessKey_returns401Unauthorized() {
        seedUserWithRole("raw.key.user@ams.lk", "RawPass123!", "TENANT_RESIDENT", AccountStatus.ACTIVE);
        String rawToken = loginAndGetRawToken("raw.key.user@ams.lk", "RawPass123!");

        HttpEntity<Void> requestEntity = new HttpEntity<>(authHeaders(rawToken));
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/users/me",
                HttpMethod.GET,
                requestEntity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}

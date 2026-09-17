package lk.ac.kelaniya.ams.identity_access_service.integration;

import lk.ac.kelaniya.ams.identity_access_service.dto.request.LoginRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests verifying rate limiting on public auth endpoints
 * within the full Spring Security filter chain and embedded HTTP server.
 */
class RateLimitingIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("POST /api/v1/auth/login enforces 10 requests/min and returns 429 with Retry-After and ErrorResponse")
    void testLoginRateLimitingEndToEnd() {
        LoginRequest badCredentials = LoginRequest.builder()
                .email("test.ratelimit@example.com")
                .password("WrongPassword123")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<LoginRequest> entity = new HttpEntity<>(badCredentials, headers);

        // First 10 requests reach the application/auth service and return 401 INVALID_CREDENTIALS
        for (int i = 1; i <= 10; i++) {
            ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                    "/api/v1/auth/login",
                    entity,
                    ErrorResponse.class
            );
            assertThat(response.getStatusCode())
                    .as("Request %d should reach auth service and return 401", i)
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        // 11th request is caught by RateLimitingFilter before reaching controller, returning 429
        ResponseEntity<ErrorResponse> rateLimitedResponse = restTemplate.postForEntity(
                "/api/v1/auth/login",
                entity,
                ErrorResponse.class
        );

        assertThat(rateLimitedResponse.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(rateLimitedResponse.getHeaders().getFirst("Retry-After")).isNotNull();
        int retryAfter = Integer.parseInt(rateLimitedResponse.getHeaders().getFirst("Retry-After"));
        assertThat(retryAfter).isGreaterThan(0);

        ErrorResponse body = rateLimitedResponse.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getError()).isNotNull();
        assertThat(body.getError().getCode()).isEqualTo("RATE_LIMIT_EXCEEDED");
        assertThat(body.getError().getMessage()).isEqualTo("Too many requests. Please try again later.");
    }

    @Test
    @DisplayName("Endpoints outside rate-limiting scope (e.g. /api/v1/auth/logout) are not blocked by rate limiting")
    void testOutOfScopeEndpointsNotRateLimited() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // Sending multiple requests to /api/v1/auth/logout without a token returns 401, never 429
        for (int i = 0; i < 15; i++) {
            ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                    "/api/v1/auth/logout",
                    HttpMethod.POST,
                    entity,
                    ErrorResponse.class
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }
    }
}

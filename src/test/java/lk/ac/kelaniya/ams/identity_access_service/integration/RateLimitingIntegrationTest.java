package lk.ac.kelaniya.ams.identity_access_service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.ac.kelaniya.ams.identity_access_service.dto.request.LoginRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration tests verifying rate limiting on public auth endpoints
 * within the full Spring Security filter chain and embedded HTTP server.
 */
@AutoConfigureMockMvc
class RateLimitingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /api/v1/auth/login enforces 10 requests/min and returns 429 with Retry-After and ErrorResponse via TestRestTemplate")
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
    @DisplayName("Cross-IP isolation in full security filter chain: exhausting limit on IP A does not impact IP B")
    void testCrossIpIsolationInSecurityFilterChain() throws Exception {
        String ipA = "198.51.100.1";
        String ipB = "198.51.100.2";

        LoginRequest loginPayload = LoginRequest.builder()
                .email("isolated.user@example.com")
                .password("WrongPass123")
                .build();
        String jsonBody = objectMapper.writeValueAsString(loginPayload);

        // IP A exhausts all 10 permits on /api/v1/auth/login -> receives 401 each time
        for (int i = 1; i <= 10; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .with(request -> {
                                request.setRemoteAddr(ipA);
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonBody))
                    .andExpect(status().isUnauthorized());
        }

        // IP A's 11th request hits the RateLimitingFilter in the filter chain -> 429 Too Many Requests
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(ipA);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.error.message").value("Too many requests. Please try again later."));

        // IP B hits the same endpoint with a different remote address -> passes filter chain normally (401, not 429)
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(ipB);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Endpoints outside rate-limiting scope (/api/v1/auth/logout, /api/v1/users/me) are not blocked by rate limiting")
    void testOutOfScopeEndpointsNotRateLimited() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // 1. POST /api/v1/auth/logout: 15 consecutive requests without token return 401 (auth failure), never 429
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

        // 2. GET /api/v1/users/me: 15 consecutive requests without token return 401 (auth failure), never 429
        for (int i = 0; i < 15; i++) {
            ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                    "/api/v1/users/me",
                    HttpMethod.GET,
                    entity,
                    ErrorResponse.class
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }
    }
}

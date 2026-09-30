package lk.ac.kelaniya.ams.identity_access_service.exception;

import lk.ac.kelaniya.ams.identity_access_service.dto.response.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("UntrustedServiceException is mapped to 401 UNAUTHORIZED with safe message Authentication required (no echoed service names)")
    void testHandleUntrustedServiceException_returns401Unauthorized() {
        UntrustedServiceException exception = new UntrustedServiceException("Untrusted or unrecognized service: 'malicious-service'");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleUntrustedServiceException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo("UNAUTHORIZED");
        assertThat(response.getBody().getError().getMessage()).isEqualTo("Authentication required");
    }

    @Test
    @DisplayName("JwtException is mapped to 401 UNAUTHORIZED with safe message Authentication required")
    void testHandleJwtException_returns401Unauthorized() {
        io.jsonwebtoken.JwtException exception = new io.jsonwebtoken.JwtException("Malformed JWT");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleJwtException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo("UNAUTHORIZED");
        assertThat(response.getBody().getError().getMessage()).isEqualTo("Authentication required");
    }

    @Test
    @DisplayName("AuthenticationException is mapped to 401 UNAUTHORIZED with safe message Authentication required")
    void testHandleAuthenticationException_returns401Unauthorized() {
        org.springframework.security.authentication.BadCredentialsException exception =
                new org.springframework.security.authentication.BadCredentialsException("Bad credentials");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAuthenticationException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo("UNAUTHORIZED");
        assertThat(response.getBody().getError().getMessage()).isEqualTo("Authentication required");
    }

    @Test
    @DisplayName("AccessDeniedException is mapped to 403 FORBIDDEN with standard JSON ErrorResponse")
    void testHandleAccessDeniedException_returns403Forbidden() {
        AccessDeniedException exception = new AccessDeniedException("Access denied: insufficient permissions");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAccessDeniedException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo("FORBIDDEN");
        assertThat(response.getBody().getError().getMessage()).isEqualTo("Access denied: insufficient permissions");
    }

    @Test
    @DisplayName("InvalidCredentialsException is mapped to 401 UNAUTHORIZED with standard JSON ErrorResponse")
    void testHandleInvalidCredentials_returns401Unauthorized() {
        InvalidCredentialsException exception = new InvalidCredentialsException("Invalid email or password");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleInvalidCredentials(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(response.getBody().getError().getMessage()).isEqualTo("Invalid email or password");
    }

    @Test
    @DisplayName("Generic unhandled exception is mapped to 500 INTERNAL_SERVER_ERROR")
    void testHandleGenericException_returns500InternalServerError() {
        Exception exception = new RuntimeException("Unexpected error");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGenericException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo("INTERNAL_SERVER_ERROR");
    }
}

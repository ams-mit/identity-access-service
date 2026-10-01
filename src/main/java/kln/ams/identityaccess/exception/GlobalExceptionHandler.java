package kln.ams.identityaccess.exception;

import io.jsonwebtoken.JwtException;
import kln.ams.identityaccess.dto.response.ApiErrorResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Locale;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Data
    @AllArgsConstructor
    public static class FieldViolation {
        private String field;
        private String message;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> new FieldViolation(err.getField(), err.getDefaultMessage()))
                .toList();

        log.warn("Validation failed for request: {}", violations);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of("VALIDATION_ERROR", "Validation failed", violations));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadableException(HttpMessageNotReadableException ex) {
        log.warn("Malformed JSON request: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of("VALIDATION_ERROR", "Malformed JSON request body"));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex) {
        log.warn("API exception [{}]: {}", ex.getCode(), ex.getMessage());
        return ResponseEntity
                .status(ex.getStatus())
                .body(ApiErrorResponse.of(ex.getCode(), ex.getMessage(), ex.getDetails()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationException(AuthenticationException ex, jakarta.servlet.http.HttpServletRequest request) {
        log.warn("Authentication failed: {}", ex.getMessage());
        String uri = request != null ? request.getRequestURI() : null;
        String code = (uri != null && uri.startsWith("/api/v1/internal")) ? "INVALID_SERVICE_TOKEN" : "INVALID_TOKEN";
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrorResponse.of(code, "Authentication failed: " + ex.getMessage()));
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ApiErrorResponse> handleJwtException(JwtException ex, jakarta.servlet.http.HttpServletRequest request) {
        log.warn("JWT validation error: {}", ex.getMessage());
        String uri = request != null ? request.getRequestURI() : null;
        String code = (uri != null && uri.startsWith("/api/v1/internal")) ? "INVALID_SERVICE_TOKEN" : "INVALID_TOKEN";
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrorResponse.of(code, "Invalid or expired token"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.of("PERMISSION_DENIED", "Access denied: insufficient permissions"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        Throwable rootCause = ex.getMostSpecificCause();
        String rootMsg = rootCause.getMessage() != null ? rootCause.getMessage().toLowerCase(Locale.ROOT) : "";
        log.warn("Data integrity conflict: {}", rootMsg);

        if (rootMsg.contains("username") || rootMsg.contains("email")) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(ApiErrorResponse.of("USER_ALREADY_EXISTS", "User already exists with this email or username"));
        } else if (rootMsg.contains("name")) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(ApiErrorResponse.of("ROLE_ALREADY_EXISTS", "Role already exists"));
        }

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("DUPLICATE_RESOURCE", "A resource conflict occurred while processing the request"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(Exception ex) {
        log.error("Unhandled internal server error: ", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of("INTERNAL_SERVER_ERROR", "An unexpected server error occurred. Please contact system administrator."));
    }
}

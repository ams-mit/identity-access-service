package kln.ams.identityaccess.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Exception thrown when business or format validation fails according to Project A Global API Standard.
 */
public class ValidationException extends ApiException {

    public ValidationException(String message) {
        super("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST);
    }

    public ValidationException(String field, String message) {
        super("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST, List.of(new GlobalExceptionHandler.FieldViolation(field, message)));
    }

    public ValidationException(String message, Object details) {
        super("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST, details);
    }
}

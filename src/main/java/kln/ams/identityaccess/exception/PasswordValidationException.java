package kln.ams.identityaccess.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

public class PasswordValidationException extends ApiException {

    public PasswordValidationException(String message) {
        super("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST);
    }

    public PasswordValidationException(String field, String message) {
        super("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST, List.of(new GlobalExceptionHandler.FieldViolation(field, message)));
    }

    public PasswordValidationException(String message, Object details) {
        super("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST, details);
    }
}

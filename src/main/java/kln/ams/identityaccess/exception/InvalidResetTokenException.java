package kln.ams.identityaccess.exception;

import org.springframework.http.HttpStatus;

public class InvalidResetTokenException extends ApiException {

    public static final String DEFAULT_MESSAGE = "Invalid or expired password reset token";

    public InvalidResetTokenException() {
        super("INVALID_RESET_TOKEN", DEFAULT_MESSAGE, HttpStatus.BAD_REQUEST);
    }

    public InvalidResetTokenException(String message) {
        super("INVALID_RESET_TOKEN", message, HttpStatus.BAD_REQUEST);
    }
}

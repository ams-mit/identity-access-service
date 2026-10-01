package kln.ams.identityaccess.exception;

import org.springframework.http.HttpStatus;

public class InvalidTokenException extends ApiException {

    public InvalidTokenException(String message) {
        super("INVALID_TOKEN", message, HttpStatus.UNAUTHORIZED);
    }
}

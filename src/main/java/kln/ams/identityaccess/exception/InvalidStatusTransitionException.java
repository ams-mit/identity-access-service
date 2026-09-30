package kln.ams.identityaccess.exception;

import org.springframework.http.HttpStatus;

public class InvalidStatusTransitionException extends ApiException {

    public InvalidStatusTransitionException(String message) {
        super("INVALID_STATUS_TRANSITION", message, HttpStatus.CONFLICT);
    }
}

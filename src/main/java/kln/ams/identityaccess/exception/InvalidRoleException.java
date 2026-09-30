package kln.ams.identityaccess.exception;

import org.springframework.http.HttpStatus;

public class InvalidRoleException extends ApiException {

    public InvalidRoleException(String message) {
        super("INVALID_ROLE", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}

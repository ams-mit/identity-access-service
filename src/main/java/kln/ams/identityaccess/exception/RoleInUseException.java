package kln.ams.identityaccess.exception;

import org.springframework.http.HttpStatus;

public class RoleInUseException extends ApiException {

    public RoleInUseException(String message) {
        super("ROLE_IN_USE", message, HttpStatus.CONFLICT);
    }
}

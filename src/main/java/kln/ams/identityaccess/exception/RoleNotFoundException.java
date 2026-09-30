package kln.ams.identityaccess.exception;

import org.springframework.http.HttpStatus;

public class RoleNotFoundException extends ApiException {

    public RoleNotFoundException(String message) {
        super("ROLE_NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }
}

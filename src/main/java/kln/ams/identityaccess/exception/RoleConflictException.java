package kln.ams.identityaccess.exception;

import org.springframework.http.HttpStatus;

public class RoleConflictException extends ApiException {

    public RoleConflictException(String message) {
        super("ROLE_CONFLICT", message, HttpStatus.CONFLICT);
    }

    public RoleConflictException(String code, String message) {
        super(code, message, HttpStatus.CONFLICT);
    }
}

package kln.ams.identityaccess.exception;

import org.springframework.http.HttpStatus;

public class PermissionNotFoundException extends ApiException {

    public PermissionNotFoundException(String message) {
        super("PERMISSION_NOT_FOUND", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}

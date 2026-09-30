package kln.ams.identityaccess.exception;

import org.springframework.http.HttpStatus;

public class CallerServiceNotAllowedException extends ApiException {

    public CallerServiceNotAllowedException(String message) {
        super("CALLER_SERVICE_NOT_ALLOWED", message, HttpStatus.FORBIDDEN);
    }
}

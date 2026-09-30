package kln.ams.identityaccess.exception;

import org.springframework.http.HttpStatus;

public class DuplicateResourceException extends ApiException {

    public DuplicateResourceException(String code, String message) {
        super(code, message, HttpStatus.CONFLICT);
    }
}

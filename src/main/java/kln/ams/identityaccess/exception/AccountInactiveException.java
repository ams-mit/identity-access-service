package kln.ams.identityaccess.exception;

import org.springframework.http.HttpStatus;

public class AccountInactiveException extends ApiException {

    public AccountInactiveException() {
        super("ACCOUNT_INACTIVE", "Account is not active or has been suspended", HttpStatus.FORBIDDEN);
    }

    public AccountInactiveException(String message) {
        super("ACCOUNT_INACTIVE", message, HttpStatus.FORBIDDEN);
    }
}

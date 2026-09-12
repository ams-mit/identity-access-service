package lk.ac.kelaniya.ams.identity_access_service.exception;

import lombok.Getter;

/**
 * Thrown when a user account status forbids authentication or operations (e.g. PENDING_VERIFICATION, SUSPENDED, DEACTIVATED).
 */
@Getter
public class AccountStatusException extends RuntimeException {

    private final String errorCode;

    public AccountStatusException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}

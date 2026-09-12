package lk.ac.kelaniya.ams.identity_access_service.exception;

/**
 * Thrown when an account registration is attempted with an already registered email address.
 */
public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String message) {
        super(message);
    }
}

package lk.ac.kelaniya.ams.identity_access_service.exception;

/**
 * Thrown when the password and confirm password inputs do not match.
 */
public class PasswordMismatchException extends RuntimeException {

    public PasswordMismatchException(String message) {
        super(message);
    }
}

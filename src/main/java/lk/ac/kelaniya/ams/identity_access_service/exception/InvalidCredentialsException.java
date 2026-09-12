package lk.ac.kelaniya.ams.identity_access_service.exception;

/**
 * Thrown when user credentials (email or password) are invalid.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}

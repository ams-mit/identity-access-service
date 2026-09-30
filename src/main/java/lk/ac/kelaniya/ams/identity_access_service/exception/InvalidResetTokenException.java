package lk.ac.kelaniya.ams.identity_access_service.exception;

/**
 * Exception thrown when a password reset token is invalid, expired, or already used.
 * The message and HTTP status are kept strictly uniform to enforce anti-enumeration.
 */
public class InvalidResetTokenException extends RuntimeException {

    public InvalidResetTokenException(String message) {
        super(message);
    }
}

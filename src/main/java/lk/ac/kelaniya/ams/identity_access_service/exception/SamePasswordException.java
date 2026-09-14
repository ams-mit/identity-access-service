package lk.ac.kelaniya.ams.identity_access_service.exception;

/**
 * Thrown when the new password is identical to the current password during a password change request.
 */
public class SamePasswordException extends RuntimeException {

    public SamePasswordException(String message) {
        super(message);
    }
}

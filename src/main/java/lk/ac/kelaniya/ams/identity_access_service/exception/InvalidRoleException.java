package lk.ac.kelaniya.ams.identity_access_service.exception;

/**
 * Thrown when an invalid or unrecognized role name is supplied for role assignment or removal.
 */
public class InvalidRoleException extends RuntimeException {

    public InvalidRoleException(String message) {
        super(message);
    }
}

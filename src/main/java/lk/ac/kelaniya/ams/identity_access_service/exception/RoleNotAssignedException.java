package lk.ac.kelaniya.ams.identity_access_service.exception;

/**
 * Thrown when attempting to remove a role that the user does not currently hold.
 */
public class RoleNotAssignedException extends RuntimeException {

    public RoleNotAssignedException(String message) {
        super(message);
    }
}

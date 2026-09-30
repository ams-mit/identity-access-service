package lk.ac.kelaniya.ams.identity_access_service.exception;

/**
 * Thrown when attempting to assign a role that the user already holds.
 */
public class RoleAlreadyAssignedException extends RuntimeException {

    public RoleAlreadyAssignedException(String message) {
        super(message);
    }
}

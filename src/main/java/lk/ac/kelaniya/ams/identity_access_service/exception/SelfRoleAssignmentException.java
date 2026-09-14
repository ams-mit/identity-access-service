package lk.ac.kelaniya.ams.identity_access_service.exception;

/**
 * Thrown when an administrator attempts to assign or remove roles on their own account.
 */
public class SelfRoleAssignmentException extends RuntimeException {

    public SelfRoleAssignmentException(String message) {
        super(message);
    }
}

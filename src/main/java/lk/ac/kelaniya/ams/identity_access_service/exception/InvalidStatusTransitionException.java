package lk.ac.kelaniya.ams.identity_access_service.exception;

/**
 * Thrown when an account lifecycle status transition violates the state machine rules
 * or when a required transition reason is missing.
 */
public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(String message) {
        super(message);
    }
}

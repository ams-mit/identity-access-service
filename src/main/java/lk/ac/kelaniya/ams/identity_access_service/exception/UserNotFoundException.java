package lk.ac.kelaniya.ams.identity_access_service.exception;

/**
 * Thrown when a requested user entity does not exist in the database.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String message) {
        super(message);
    }
}

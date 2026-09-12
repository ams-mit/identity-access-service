package lk.ac.kelaniya.ams.identity_access_service.exception;

import lombok.Getter;

import java.time.Instant;

/**
 * Thrown when an account is temporarily locked due to exceeding the maximum allowed failed login attempts.
 */
@Getter
public class AccountLockedException extends RuntimeException {

    private final Instant lockedUntil;

    public AccountLockedException(String message, Instant lockedUntil) {
        super(message);
        this.lockedUntil = lockedUntil;
    }

    public AccountLockedException(String message) {
        super(message);
        this.lockedUntil = null;
    }
}

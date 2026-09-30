package lk.ac.kelaniya.ams.identity_access_service.security.ratelimit;

/**
 * Encapsulates the outcome of a rate-limiting check.
 *
 * @param allowed           true if the request is within rate limits and permitted to proceed; false otherwise.
 * @param retryAfterSeconds remaining time in whole seconds until the rate limit bucket refills enough to permit requests.
 */
public record RateLimitResult(boolean allowed, long retryAfterSeconds) {

    public static RateLimitResult permitted() {
        return new RateLimitResult(true, 0);
    }

    public static RateLimitResult exceeded(long retryAfterSeconds) {
        return new RateLimitResult(false, retryAfterSeconds);
    }
}

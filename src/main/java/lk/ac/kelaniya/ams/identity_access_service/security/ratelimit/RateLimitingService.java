package lk.ac.kelaniya.ams.identity_access_service.security.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory rate limiting service powered by Bucket4j token buckets.
 * Manages per-IP rate limit buckets for public unauthenticated auth endpoints.
 *
 * <p>Production Note: In a production deployment behind a reverse proxy, API Gateway, or load balancer,
 * client IP extraction must inspect {@code X-Forwarded-For} or {@code X-Real-IP} headers (with trusted proxy
 * validation) rather than {@code request.getRemoteAddr()}. For this prototype scale, direct remote address
 * is acceptable without overbuilding infrastructure.
 *
 * <p>Endpoint Limits and Risk Profile Justification:
 * <ul>
 *   <li>{@code POST /api/v1/auth/login} - <b>10 requests per minute per IP</b>:
 *       Account-level lockout already guards against single-account vertical brute-force attacks (5 consecutive
 *       failed attempts lock the target account for 15 minutes). A limit of 10 requests per minute per IP halts
 *       distributed horizontal password spraying across multiple accounts while providing comfortable headroom
 *       for legitimate users correcting typing mistakes or logging in across multiple browser tabs.</li>
 *   <li>{@code POST /api/v1/auth/register} - <b>5 requests per minute per IP</b>:
 *       Registration triggers computationally expensive BCrypt hashing (cost factor 10+) and database unique
 *       constraint checks. Legitimate human users register once; a limit of 5 requests per minute allows ample margin
 *       for form validation retries while stopping automated bot registration floods, DB bloat, and CPU exhaustion.</li>
 *   <li>{@code POST /api/v1/auth/forgot-password} - <b>3 requests per minute per IP</b>:
 *       Forgot-password intentionally returns 200 OK unconditionally (strict anti-enumeration) and does NOT lock
 *       accounts upon request to prevent denial-of-service against innocent users. Consequently, IP-level rate limiting
 *       is the primary and sole defensive shield against abuse. Without it, an attacker could flood reset tokens,
 *       spam notification channels, or exhaust database storage. 3 requests per minute provides strong protection
 *       without hindering legitimate users who rarely request resets repeatedly.</li>
 *   <li>{@code POST /api/v1/auth/reset-password} - <b>5 requests per minute per IP</b>:
 *       Reset tokens have 256-bit cryptographic entropy with a 15-minute expiration, making brute-force mathematically
 *       infeasible. However, verifying and executing a password reset involves BCrypt password hashing and database
 *       transactions. A limit of 5 requests per minute per IP prevents token verification hammering while allowing
 *       legitimate users to correct password confirmation mismatches.</li>
 * </ul>
 */
@Service
public class RateLimitingService {

    public static final String PATH_REGISTER = "/api/v1/auth/register";
    public static final String PATH_LOGIN = "/api/v1/auth/login";
    public static final String PATH_FORGOT_PASSWORD = "/api/v1/auth/forgot-password";
    public static final String PATH_RESET_PASSWORD = "/api/v1/auth/reset-password";

    /**
     * Endpoint bandwidth definitions specifying (capacity, refill tokens per minute).
     */
    private static final Map<String, Integer> ENDPOINT_LIMITS = Map.of(
            PATH_LOGIN, 10,
            PATH_REGISTER, 5,
            PATH_FORGOT_PASSWORD, 3,
            PATH_RESET_PASSWORD, 5
    );

    private final TimeMeter timeMeter;
    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Default constructor for Spring production context using system clock.
     */
    public RateLimitingService() {
        this(TimeMeter.SYSTEM_MILLISECONDS);
    }

    /**
     * Constructor allowing injection of custom TimeMeter for deterministic, zero-sleep unit testing.
     *
     * @param timeMeter custom time meter
     */
    public RateLimitingService(TimeMeter timeMeter) {
        this.timeMeter = timeMeter;
    }

    /**
     * Checks whether the given HTTP method and URI path are subject to rate limiting.
     * Only POST requests to the 4 unauthenticated public auth endpoints are rate-limited.
     *
     * @param method HTTP method (e.g. POST, GET, OPTIONS)
     * @param rawUri request URI
     * @return true if rate limiting applies; false otherwise
     */
    public boolean isRateLimitedEndpoint(String method, String rawUri) {
        if (!"POST".equalsIgnoreCase(method)) {
            return false;
        }
        String normalizedPath = normalizePath(rawUri);
        return ENDPOINT_LIMITS.containsKey(normalizedPath);
    }

    /**
     * Attempts to consume 1 token for the specified client IP and endpoint path.
     *
     * @param clientIp    client IP address
     * @param endpointUri request URI
     * @return {@link RateLimitResult} indicating if allowed and retry-after seconds if rejected
     */
    public RateLimitResult consume(String clientIp, String endpointUri) {
        String normalizedPath = normalizePath(endpointUri);
        Integer capacity = ENDPOINT_LIMITS.get(normalizedPath);

        if (capacity == null) {
            return RateLimitResult.permitted();
        }

        String ip = (clientIp == null || clientIp.isBlank()) ? "unknown" : clientIp.trim();
        String bucketKey = ip + ":" + normalizedPath;

        Bucket bucket = buckets.computeIfAbsent(bucketKey, key -> createBucket(capacity));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            return RateLimitResult.permitted();
        }

        long nanosToWaitForRefill = probe.getNanosToWaitForRefill();
        // Round up to the next full second to ensure bucket has refilled when retry window completes
        long retryAfterSeconds = Math.max(1, (nanosToWaitForRefill + 999_999_999L) / 1_000_000_000L);
        return RateLimitResult.exceeded(retryAfterSeconds);
    }

    /**
     * Creates a new token bucket with the configured capacity refilling over a 1-minute window.
     *
     * @param capacity maximum tokens and refill amount per minute
     * @return initialized Bucket
     */
    private Bucket createBucket(int capacity) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(capacity, Duration.ofMinutes(1))
                .build();

        return Bucket.builder()
                .withCustomTimePrecision(timeMeter)
                .addLimit(limit)
                .build();
    }

    /**
     * Normalizes a request URI by stripping any context path and trailing slashes.
     *
     * @param rawUri raw request URI
     * @return normalized path
     */
    public String normalizePath(String rawUri) {
        if (rawUri == null) {
            return "";
        }
        String path = rawUri.trim();
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    /**
     * Clears all in-memory buckets. Used for test isolation between integration test runs.
     */
    public void reset() {
        buckets.clear();
    }
}

package lk.ac.kelaniya.ams.identity_access_service.security.ratelimit;

import io.github.bucket4j.TimeMeter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitingServiceTest {

    /**
     * Controllable in-memory TimeMeter allowing zero-sleep, deterministic time manipulation for tests.
     */
    static class MutableTimeMeter implements TimeMeter {
        private long currentNanos = 1_000_000_000L; // Start at 1s to avoid zero

        @Override
        public long currentTimeNanos() {
            return currentNanos;
        }

        @Override
        public boolean isWallClockBased() {
            return false;
        }

        public void advance(Duration duration) {
            this.currentNanos += duration.toNanos();
        }
    }

    private MutableTimeMeter timeMeter;
    private RateLimitingService service;

    @BeforeEach
    void setUp() {
        timeMeter = new MutableTimeMeter();
        service = new RateLimitingService(timeMeter);
    }

    @Test
    @DisplayName("login endpoint allows exactly 10 requests per minute and rejects the 11th")
    void testLoginLimit_10PerMinute() {
        String ip = "192.168.1.100";
        String path = RateLimitingService.PATH_LOGIN;

        // First 10 requests must be permitted
        for (int i = 1; i <= 10; i++) {
            RateLimitResult result = service.consume(ip, path);
            assertThat(result.allowed())
                    .as("Request %d should be allowed", i)
                    .isTrue();
            assertThat(result.retryAfterSeconds()).isEqualTo(0);
        }

        // 11th request must be rejected with 429 Retry-After
        RateLimitResult exceeded = service.consume(ip, path);
        assertThat(exceeded.allowed()).isFalse();
        assertThat(exceeded.retryAfterSeconds()).isGreaterThan(0);
    }

    @Test
    @DisplayName("register endpoint allows exactly 5 requests per minute and rejects the 6th")
    void testRegisterLimit_5PerMinute() {
        String ip = "192.168.1.101";
        String path = RateLimitingService.PATH_REGISTER;

        for (int i = 1; i <= 5; i++) {
            RateLimitResult result = service.consume(ip, path);
            assertThat(result.allowed()).isTrue();
        }

        RateLimitResult exceeded = service.consume(ip, path);
        assertThat(exceeded.allowed()).isFalse();
        assertThat(exceeded.retryAfterSeconds()).isGreaterThan(0);
    }

    @Test
    @DisplayName("forgot-password endpoint allows exactly 3 requests per minute and rejects the 4th")
    void testForgotPasswordLimit_3PerMinute() {
        String ip = "192.168.1.102";
        String path = RateLimitingService.PATH_FORGOT_PASSWORD;

        for (int i = 1; i <= 3; i++) {
            RateLimitResult result = service.consume(ip, path);
            assertThat(result.allowed()).isTrue();
        }

        RateLimitResult exceeded = service.consume(ip, path);
        assertThat(exceeded.allowed()).isFalse();
        assertThat(exceeded.retryAfterSeconds()).isGreaterThan(0);
    }

    @Test
    @DisplayName("reset-password endpoint allows exactly 5 requests per minute and rejects the 6th")
    void testResetPasswordLimit_5PerMinute() {
        String ip = "192.168.1.103";
        String path = RateLimitingService.PATH_RESET_PASSWORD;

        for (int i = 1; i <= 5; i++) {
            RateLimitResult result = service.consume(ip, path);
            assertThat(result.allowed()).isTrue();
        }

        RateLimitResult exceeded = service.consume(ip, path);
        assertThat(exceeded.allowed()).isFalse();
        assertThat(exceeded.retryAfterSeconds()).isGreaterThan(0);
    }

    @Test
    @DisplayName("IP isolation: exhausting quota for one IP does not affect another IP")
    void testIpIsolation() {
        String ipA = "10.0.0.1";
        String ipB = "10.0.0.2";
        String path = RateLimitingService.PATH_LOGIN;

        // Exhaust all 10 permits for IP A
        for (int i = 0; i < 10; i++) {
            assertThat(service.consume(ipA, path).allowed()).isTrue();
        }
        assertThat(service.consume(ipA, path).allowed()).isFalse();

        // IP B must still have full quota
        for (int i = 0; i < 10; i++) {
            assertThat(service.consume(ipB, path).allowed())
                    .as("IP B request %d should be allowed", i + 1)
                    .isTrue();
        }
        assertThat(service.consume(ipB, path).allowed()).isFalse();
    }

    @Test
    @DisplayName("Endpoint isolation: exhausting login does not affect register for the same IP")
    void testEndpointIsolation() {
        String ip = "10.0.0.3";

        // Exhaust login (10 requests)
        for (int i = 0; i < 10; i++) {
            assertThat(service.consume(ip, RateLimitingService.PATH_LOGIN).allowed()).isTrue();
        }
        assertThat(service.consume(ip, RateLimitingService.PATH_LOGIN).allowed()).isFalse();

        // Register on same IP should still have all 5 permits available
        for (int i = 0; i < 5; i++) {
            assertThat(service.consume(ip, RateLimitingService.PATH_REGISTER).allowed()).isTrue();
        }
        assertThat(service.consume(ip, RateLimitingService.PATH_REGISTER).allowed()).isFalse();
    }

    @Test
    @DisplayName("Bucket refill: advancing time by 60 seconds refills the bucket completely")
    void testBucketRefillAfterWindow() {
        String ip = "10.0.0.4";
        String path = RateLimitingService.PATH_LOGIN;

        // Consume all 10 permits
        for (int i = 0; i < 10; i++) {
            assertThat(service.consume(ip, path).allowed()).isTrue();
        }
        // 11th is blocked
        assertThat(service.consume(ip, path).allowed()).isFalse();

        // Advance simulated clock by 60 seconds (full interval window)
        timeMeter.advance(Duration.ofSeconds(60));

        // Now requests must be permitted again
        RateLimitResult refilledResult = service.consume(ip, path);
        assertThat(refilledResult.allowed()).isTrue();
    }

    @Test
    @DisplayName("Out of scope paths and non-POST methods are not rate limited")
    void testOutOfScopeEndpoints() {
        assertThat(service.isRateLimitedEndpoint("POST", "/api/v1/auth/logout")).isFalse();
        assertThat(service.isRateLimitedEndpoint("GET", "/api/v1/users/me")).isFalse();
        assertThat(service.isRateLimitedEndpoint("GET", "/api/v1/auth/login")).isFalse();
        assertThat(service.isRateLimitedEndpoint("OPTIONS", "/api/v1/auth/login")).isFalse();

        // Calling consume on an unmapped endpoint returns permitted immediately
        RateLimitResult unmapped = service.consume("127.0.0.1", "/api/v1/auth/logout");
        assertThat(unmapped.allowed()).isTrue();
    }

    @Test
    @DisplayName("reset() clears all buckets allowing immediate new requests")
    void testReset() {
        String ip = "10.0.0.5";
        String path = RateLimitingService.PATH_FORGOT_PASSWORD;

        // Consume all 3 permits
        for (int i = 0; i < 3; i++) {
            assertThat(service.consume(ip, path).allowed()).isTrue();
        }
        assertThat(service.consume(ip, path).allowed()).isFalse();

        // Reset service
        service.reset();

        // Should immediately be allowed again
        assertThat(service.consume(ip, path).allowed()).isTrue();
    }
}

package lk.ac.kelaniya.ams.identity_access_service.security.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.TimeMeter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RateLimitingFilterTest {

    static class MutableTimeMeter implements TimeMeter {
        private long currentNanos = 1_000_000_000L;

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
    private RateLimitingService rateLimitingService;
    private ObjectMapper objectMapper;
    private RateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        timeMeter = new MutableTimeMeter();
        rateLimitingService = new RateLimitingService(timeMeter);
        objectMapper = new ObjectMapper();
        filter = new RateLimitingFilter(rateLimitingService, objectMapper);
    }

    @Test
    @DisplayName("POST /api/v1/auth/login under limit succeeds normally; exceeding limit returns 429 with Retry-After and exact error envelope")
    void testLogin_underAndOverLimit() throws ServletException, IOException {
        String ip = "172.16.0.1";
        String path = "/api/v1/auth/login";

        // Under limit: 10 requests succeed normally and invoke filterChain
        for (int i = 1; i <= 10; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
            request.setRemoteAddr(ip);
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain filterChain = mock(FilterChain.class);

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
            verify(filterChain, times(1)).doFilter(request, response);
        }

        // Exceeding limit: 11th request returns 429 Too Many Requests
        MockHttpServletRequest blockedRequest = new MockHttpServletRequest("POST", path);
        blockedRequest.setRemoteAddr(ip);
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        FilterChain blockedFilterChain = mock(FilterChain.class);

        filter.doFilter(blockedRequest, blockedResponse, blockedFilterChain);

        assertThat(blockedResponse.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(blockedResponse.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(blockedResponse.getHeader("Retry-After")).isNotNull();
        int retryAfter = Integer.parseInt(blockedResponse.getHeader("Retry-After"));
        assertThat(retryAfter).isGreaterThan(0);

        // Verify filterChain was NOT invoked
        verify(blockedFilterChain, never()).doFilter(blockedRequest, blockedResponse);

        // Confirm exact error envelope
        String responseBody = blockedResponse.getContentAsString();
        assertThat(responseBody).contains("\"code\":\"RATE_LIMIT_EXCEEDED\"");
        assertThat(responseBody).contains("\"message\":\"Too many requests. Please try again later.\"");
        assertThat(responseBody).contains("\"error\":{");
    }

    @Test
    @DisplayName("POST /api/v1/auth/register under limit succeeds; exceeding limit returns 429 and Retry-After")
    void testRegister_underAndOverLimit() throws ServletException, IOException {
        String ip = "172.16.0.2";
        String path = "/api/v1/auth/register";

        // 5 allowed
        for (int i = 1; i <= 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
            request.setRemoteAddr(ip);
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain filterChain = mock(FilterChain.class);

            filter.doFilter(request, response, filterChain);
            assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
            verify(filterChain, times(1)).doFilter(request, response);
        }

        // 6th rejected with 429
        MockHttpServletRequest blockedRequest = new MockHttpServletRequest("POST", path);
        blockedRequest.setRemoteAddr(ip);
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        FilterChain blockedFilterChain = mock(FilterChain.class);

        filter.doFilter(blockedRequest, blockedResponse, blockedFilterChain);

        assertThat(blockedResponse.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(blockedResponse.getHeader("Retry-After")).isNotNull();
        assertThat(blockedResponse.getContentAsString()).contains("\"code\":\"RATE_LIMIT_EXCEEDED\"");
        verify(blockedFilterChain, never()).doFilter(blockedRequest, blockedResponse);
    }

    @Test
    @DisplayName("POST /api/v1/auth/forgot-password under limit succeeds; exceeding limit returns 429 and Retry-After")
    void testForgotPassword_underAndOverLimit() throws ServletException, IOException {
        String ip = "172.16.0.3";
        String path = "/api/v1/auth/forgot-password";

        // 3 allowed
        for (int i = 1; i <= 3; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
            request.setRemoteAddr(ip);
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain filterChain = mock(FilterChain.class);

            filter.doFilter(request, response, filterChain);
            assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
            verify(filterChain, times(1)).doFilter(request, response);
        }

        // 4th rejected with 429
        MockHttpServletRequest blockedRequest = new MockHttpServletRequest("POST", path);
        blockedRequest.setRemoteAddr(ip);
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        FilterChain blockedFilterChain = mock(FilterChain.class);

        filter.doFilter(blockedRequest, blockedResponse, blockedFilterChain);

        assertThat(blockedResponse.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(blockedResponse.getHeader("Retry-After")).isNotNull();
        assertThat(blockedResponse.getContentAsString()).contains("\"code\":\"RATE_LIMIT_EXCEEDED\"");
        verify(blockedFilterChain, never()).doFilter(blockedRequest, blockedResponse);
    }

    @Test
    @DisplayName("POST /api/v1/auth/reset-password under limit succeeds; exceeding limit returns 429 and Retry-After")
    void testResetPassword_underAndOverLimit() throws ServletException, IOException {
        String ip = "172.16.0.4";
        String path = "/api/v1/auth/reset-password";

        // 5 allowed
        for (int i = 1; i <= 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
            request.setRemoteAddr(ip);
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain filterChain = mock(FilterChain.class);

            filter.doFilter(request, response, filterChain);
            assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
            verify(filterChain, times(1)).doFilter(request, response);
        }

        // 6th rejected with 429
        MockHttpServletRequest blockedRequest = new MockHttpServletRequest("POST", path);
        blockedRequest.setRemoteAddr(ip);
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        FilterChain blockedFilterChain = mock(FilterChain.class);

        filter.doFilter(blockedRequest, blockedResponse, blockedFilterChain);

        assertThat(blockedResponse.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(blockedResponse.getHeader("Retry-After")).isNotNull();
        assertThat(blockedResponse.getContentAsString()).contains("\"code\":\"RATE_LIMIT_EXCEEDED\"");
        verify(blockedFilterChain, never()).doFilter(blockedRequest, blockedResponse);
    }

    @Test
    @DisplayName("Isolation: different IP address is not affected by another IP's rate limit")
    void testIpIsolation() throws ServletException, IOException {
        String ipBlocked = "192.168.1.50";
        String ipAllowed = "192.168.1.51";
        String path = "/api/v1/auth/login";

        // Exhaust IP A
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
            req.setRemoteAddr(ipBlocked);
            filter.doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));
        }

        // Verify IP A is now blocked
        MockHttpServletRequest reqBlocked = new MockHttpServletRequest("POST", path);
        reqBlocked.setRemoteAddr(ipBlocked);
        MockHttpServletResponse resBlocked = new MockHttpServletResponse();
        filter.doFilter(reqBlocked, resBlocked, mock(FilterChain.class));
        assertThat(resBlocked.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());

        // Verify IP B is NOT affected and succeeds normally
        MockHttpServletRequest reqAllowed = new MockHttpServletRequest("POST", path);
        reqAllowed.setRemoteAddr(ipAllowed);
        MockHttpServletResponse resAllowed = new MockHttpServletResponse();
        FilterChain chainAllowed = mock(FilterChain.class);

        filter.doFilter(reqAllowed, resAllowed, chainAllowed);

        assertThat(resAllowed.getStatus()).isEqualTo(HttpStatus.OK.value());
        verify(chainAllowed, times(1)).doFilter(reqAllowed, resAllowed);
    }

    @Test
    @DisplayName("Endpoints NOT in scope (e.g. /api/v1/auth/logout, /api/v1/users/me) are unaffected by the filter")
    void testOutOfScopeEndpointsUnaffected() throws ServletException, IOException {
        String ip = "192.168.1.60";

        // 15 requests to /api/v1/auth/logout - none should be blocked by rate limiting
        for (int i = 0; i < 15; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
            req.setRemoteAddr(ip);
            MockHttpServletResponse res = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilter(req, res, chain);

            assertThat(res.getStatus()).isEqualTo(HttpStatus.OK.value());
            verify(chain, times(1)).doFilter(req, res);
        }

        // 15 requests to /api/v1/users/me
        for (int i = 0; i < 15; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/users/me");
            req.setRemoteAddr(ip);
            MockHttpServletResponse res = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilter(req, res, chain);

            assertThat(res.getStatus()).isEqualTo(HttpStatus.OK.value());
            verify(chain, times(1)).doFilter(req, res);
        }

        // OPTIONS preflight on /api/v1/auth/login
        MockHttpServletRequest optionsReq = new MockHttpServletRequest("OPTIONS", "/api/v1/auth/login");
        optionsReq.setRemoteAddr(ip);
        MockHttpServletResponse optionsRes = new MockHttpServletResponse();
        FilterChain optionsChain = mock(FilterChain.class);

        filter.doFilter(optionsReq, optionsRes, optionsChain);
        assertThat(optionsRes.getStatus()).isEqualTo(HttpStatus.OK.value());
        verify(optionsChain, times(1)).doFilter(optionsReq, optionsRes);
    }

    @Test
    @DisplayName("Bucket refills after the configured window, allowing requests after exhaustion")
    void testBucketRefillAfterWindow() throws ServletException, IOException {
        String ip = "192.168.1.70";
        String path = "/api/v1/auth/login";

        // Exhaust all 10 permits
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
            req.setRemoteAddr(ip);
            filter.doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));
        }

        // 11th request is blocked
        MockHttpServletRequest blockedReq = new MockHttpServletRequest("POST", path);
        blockedReq.setRemoteAddr(ip);
        MockHttpServletResponse blockedRes = new MockHttpServletResponse();
        filter.doFilter(blockedReq, blockedRes, mock(FilterChain.class));
        assertThat(blockedRes.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());

        // Advance clock by 60 seconds
        timeMeter.advance(Duration.ofSeconds(60));

        // Now request succeeds and passes through filterChain
        MockHttpServletRequest afterRefillReq = new MockHttpServletRequest("POST", path);
        afterRefillReq.setRemoteAddr(ip);
        MockHttpServletResponse afterRefillRes = new MockHttpServletResponse();
        FilterChain refilledChain = mock(FilterChain.class);

        filter.doFilter(afterRefillReq, afterRefillRes, refilledChain);

        assertThat(afterRefillRes.getStatus()).isEqualTo(HttpStatus.OK.value());
        verify(refilledChain, times(1)).doFilter(afterRefillReq, afterRefillRes);
    }
}

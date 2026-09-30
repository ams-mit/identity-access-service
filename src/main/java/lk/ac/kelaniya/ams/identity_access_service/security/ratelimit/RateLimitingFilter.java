package lk.ac.kelaniya.ams.identity_access_service.security.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Servlet filter that intercepts incoming HTTP requests to enforce rate limits on public unauthenticated auth endpoints.
 * Returns HTTP 429 Too Many Requests with a Retry-After header and standard ErrorResponse payload when limits are exceeded.
 */
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitingService rateLimitingService;
    private final ObjectMapper objectMapper;
    private final List<IpAddressMatcher> trustedProxyMatchers;

    public RateLimitingFilter(RateLimitingService rateLimitingService, ObjectMapper objectMapper) {
        this(rateLimitingService, objectMapper, List.of());
    }

    public RateLimitingFilter(RateLimitingService rateLimitingService, ObjectMapper objectMapper, List<String> trustedProxies) {
        this.rateLimitingService = rateLimitingService;
        this.objectMapper = objectMapper;
        this.trustedProxyMatchers = (trustedProxies != null)
                ? trustedProxies.stream()
                        .filter(p -> p != null && !p.isBlank())
                        .map(String::trim)
                        .map(IpAddressMatcher::new)
                        .toList()
                : List.of();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String method = request.getMethod();
        String uri = request.getRequestURI();

        // Check if the endpoint is within the 4 rate-limited public auth paths
        if (!rateLimitingService.isRateLimitedEndpoint(method, uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);

        RateLimitResult result = rateLimitingService.consume(clientIp, uri);

        if (result.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Rate limit exceeded: return HTTP 429 Too Many Requests with Retry-After header and standard error envelope
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));

        ErrorResponse errorResponse = ErrorResponse.of(
                "RATE_LIMIT_EXCEEDED",
                "Too many requests. Please try again later."
        );

        objectMapper.writeValue(response.getWriter(), errorResponse);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (isTrustedProxy(remoteAddr)) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                String[] ips = xForwardedFor.split(",");
                String firstIp = ips[0].trim();
                if (!firstIp.isEmpty()) {
                    return firstIp;
                }
            }
        }
        return remoteAddr;
    }

    private boolean isTrustedProxy(String remoteAddr) {
        if (remoteAddr == null || trustedProxyMatchers.isEmpty()) {
            return false;
        }
        for (IpAddressMatcher matcher : trustedProxyMatchers) {
            if (matcher.matches(remoteAddr)) {
                return true;
            }
        }
        return false;
    }
}

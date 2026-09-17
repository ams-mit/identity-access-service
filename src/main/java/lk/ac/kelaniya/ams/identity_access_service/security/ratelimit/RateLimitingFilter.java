package lk.ac.kelaniya.ams.identity_access_service.security.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that intercepts incoming HTTP requests to enforce rate limits on public unauthenticated auth endpoints.
 * Returns HTTP 429 Too Many Requests with a Retry-After header and standard ErrorResponse payload when limits are exceeded.
 */
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitingService rateLimitingService;
    private final ObjectMapper objectMapper;

    public RateLimitingFilter(RateLimitingService rateLimitingService, ObjectMapper objectMapper) {
        this.rateLimitingService = rateLimitingService;
        this.objectMapper = objectMapper;
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

        // Note: In a production deployment behind a reverse proxy, API Gateway, or load balancer,
        // client IP extraction must inspect 'X-Forwarded-For' or 'X-Real-IP' headers (with trusted
        // proxy validation) rather than getRemoteAddr(). For this prototype scale, direct remote
        // address is acceptable without overbuilding infrastructure.
        String clientIp = request.getRemoteAddr();

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
}

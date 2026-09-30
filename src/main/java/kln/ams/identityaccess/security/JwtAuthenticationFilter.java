package kln.ams.identityaccess.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Jws<Claims> claimsJws = jwtService.parseAndValidateToken(token);
            Claims claims = claimsJws.getPayload();

            String tokenType = claims.get("type", String.class);
            String subject = claims.getSubject();

            if (tokenType == null || subject == null) {
                log.warn("JWT missing required type or sub claim");
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            String requestPath = request.getRequestURI();
            boolean isInternalPath = requestPath != null && requestPath.startsWith("/api/v1/internal");

            if (isInternalPath && !"service".equalsIgnoreCase(tokenType)) {
                log.warn("Rejected non-service token type '{}' on internal endpoint: {}", tokenType, requestPath);
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            if (!isInternalPath && "service".equalsIgnoreCase(tokenType)) {
                log.warn("Rejected service token on user endpoint: {}", requestPath);
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            if ("user".equalsIgnoreCase(tokenType)) {
                UUID userId = UUID.fromString(subject);
                @SuppressWarnings("unchecked")
                List<String> roles = claims.get("roles", List.class);

                UserPrincipal userPrincipal = new UserPrincipal(userId, roles);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else if ("service".equalsIgnoreCase(tokenType)) {
                ServicePrincipal servicePrincipal = new ServicePrincipal(subject);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(servicePrincipal, null, servicePrincipal.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                log.warn("Rejected JWT with unknown token type: '{}'", tokenType);
                SecurityContextHolder.clearContext();
            }
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("Invalid JWT token presented: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}

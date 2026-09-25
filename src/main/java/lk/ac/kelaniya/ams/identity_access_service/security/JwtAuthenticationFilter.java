package lk.ac.kelaniya.ams.identity_access_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Filter intercepting incoming requests to extract and validate Bearer JWT tokens.
 * Upon successful verification, populates Spring Security's SecurityContext with UserPrincipal.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Jws<Claims> claimsJws = jwtService.parseAndValidateToken(token);
                Claims claims = claimsJws.getPayload();
                String tokenType = claims.get("type", String.class);

                if ("service".equals(tokenType)) {
                    String serviceName = claims.getSubject();
                    if (serviceName == null || serviceName.isBlank()) {
                        throw new JwtException("Service token subject must not be blank");
                    }

                    ServicePrincipal principal = ServicePrincipal.builder()
                            .serviceName(serviceName)
                            .tokenType("service")
                            .build();

                    List<SimpleGrantedAuthority> authorities = List.of(
                            new SimpleGrantedAuthority("ROLE_SERVICE"),
                            new SimpleGrantedAuthority("ROLE_INTERNAL_SERVICE")
                    );

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(principal, null, authorities);

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else if ("user".equals(tokenType)) {
                    String userIdStr = claims.getSubject();
                    if (userIdStr == null || userIdStr.isBlank()) {
                        throw new JwtException("User token subject must not be blank");
                    }
                    UUID userId = UUID.fromString(userIdStr);

                    @SuppressWarnings("unchecked")
                    List<String> roles = claims.get("roles", List.class);
                    List<SimpleGrantedAuthority> authorities = (roles != null)
                            ? roles.stream()
                                    .flatMap(role -> java.util.stream.Stream.of(
                                            new SimpleGrantedAuthority(role.startsWith("ROLE_") ? role : "ROLE_" + role),
                                            new SimpleGrantedAuthority(role)
                                    ))
                                    .distinct()
                                    .toList()
                            : List.of();

                    UserPrincipal principal = UserPrincipal.builder()
                            .userId(userId)
                            .roles(roles != null ? roles : List.of())
                            .build();

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(principal, null, authorities);

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    log.warn("Rejected JWT with invalid or missing type claim: '{}'", tokenType);
                    throw new JwtException("Invalid JWT type claim: '" + tokenType + "'. Must be exactly 'user' or 'service'.");
                }
            } catch (JwtException | IllegalArgumentException ex) {
                log.warn("Invalid JWT token provided: {}", ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}

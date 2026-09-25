package lk.ac.kelaniya.ams.identity_access_service.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.ac.kelaniya.ams.identity_access_service.security.ratelimit.RateLimitingFilter;
import lk.ac.kelaniya.ams.identity_access_service.security.ratelimit.RateLimitingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.ErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration permitting public endpoints for registration, login, JWKS, and OpenAPI,
 * enforcing Bearer JWT authentication on protected endpoints, and enabling method-level security for RBAC.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired(required = false)
    private JwtService jwtService;

    @Autowired(required = false)
    private RateLimitingService rateLimitingService;

    @Autowired(required = false)
    private ObjectMapper objectMapper;

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            ObjectMapper mapper = objectMapper != null ? objectMapper : new ObjectMapper();
                            ErrorResponse errorResponse = ErrorResponse.of(
                                    "UNAUTHORIZED",
                                    authException != null && authException.getMessage() != null && !authException.getMessage().isBlank()
                                            ? authException.getMessage()
                                            : "Full authentication is required to access this resource"
                            );
                            response.getWriter().write(mapper.writeValueAsString(errorResponse));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            ObjectMapper mapper = objectMapper != null ? objectMapper : new ObjectMapper();
                            ErrorResponse errorResponse = ErrorResponse.of("FORBIDDEN", "Access denied: insufficient permissions");
                            response.getWriter().write(mapper.writeValueAsString(errorResponse));
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password",
                                "/api/v1/auth/public-key",
                                "/api/v1/auth/jwks.json",
                                "/api/v1/auth/.well-known/**",
                                "/.well-known/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/actuator/health/**",
                                "/actuator/info"
                        ).permitAll()
                        .requestMatchers("/api/v1/auth/logout").authenticated()
                        .requestMatchers("/internal/v1/**").hasAnyRole("SERVICE", "INTERNAL_SERVICE")
                        .anyRequest().authenticated()
                );

        if (rateLimitingService != null && objectMapper != null) {
            http.addFilterBefore(new RateLimitingFilter(rateLimitingService, objectMapper), UsernamePasswordAuthenticationFilter.class);
        }

        if (jwtService != null) {
            http.addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        }

        return http.build();
    }
}

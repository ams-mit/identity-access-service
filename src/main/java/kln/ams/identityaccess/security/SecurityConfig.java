package kln.ams.identityaccess.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import kln.ams.identityaccess.dto.response.ApiErrorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RequestIdFilter requestIdFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint())
                .accessDeniedHandler(accessDeniedHandler())
            )
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()

                // Internal microservice endpoints (require SERVICE token)
                .requestMatchers("/api/v1/internal/**").hasAuthority("ROLE_SERVICE")

                // Administrative endpoints (require SYSTEM_ADMINISTRATOR role)
                .requestMatchers("/api/v1/users", "/api/v1/users/**").hasAuthority("ROLE_SYSTEM_ADMINISTRATOR")
                .requestMatchers("/api/v1/roles", "/api/v1/roles/**").hasAuthority("ROLE_SYSTEM_ADMINISTRATOR")
                .requestMatchers("/api/v1/permissions", "/api/v1/permissions/**").hasAuthority("ROLE_SYSTEM_ADMINISTRATOR")

                // Authenticated user endpoint
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()

                .anyRequest().authenticated()
            )
            .addFilterBefore(requestIdFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            String uri = request.getRequestURI();
            boolean isInternal = uri != null && uri.startsWith("/api/v1/internal");
            String errorCode = isInternal ? "INVALID_SERVICE_TOKEN" : "INVALID_TOKEN";
            String errorMessage = isInternal ? "Authentication required or service token invalid" : "Authentication required or token invalid";
            ApiErrorResponse error = ApiErrorResponse.of(errorCode, errorMessage);
            response.getWriter().write(objectMapper.writeValueAsString(error));
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiErrorResponse error = ApiErrorResponse.of("PERMISSION_DENIED", "Access denied: insufficient permissions or caller not allowed");
            response.getWriter().write(objectMapper.writeValueAsString(error));
        };
    }
}

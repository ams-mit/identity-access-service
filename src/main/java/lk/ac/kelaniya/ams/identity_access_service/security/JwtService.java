package lk.ac.kelaniya.ams.identity_access_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import lk.ac.kelaniya.ams.identity_access_service.exception.UntrustedServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Service responsible for issuing and verifying RS256-signed JSON Web Tokens (JWT).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    /**
     * Known and trusted microservice callers authorized to receive service tokens.
     */
    public static final Set<String> TRUSTED_SERVICES = Set.of(
            "resident-management-service",
            "billing-service",
            "visitor-management-service",
            "facility-management-service",
            "maintenance-service",
            "communication-service",
            "notification-service",
            "api-gateway",
            "identity-access-service"
    );

    private final RsaKeyProvider rsaKeyProvider;
    private final RsaKeyProperties rsaKeyProperties;

    /**
     * Issues an RS256-signed JWT with subject (userId), type ("user"), email, roles, iat, exp (30 min expiry),
     * and a kid header matching the JWKS endpoint.
     *
     * @param userId the unique user identifier
     * @param email  the user's email address
     * @param roles  the list of assigned user roles
     * @return compact RS256-signed JWT string
     */
    public String generateToken(UUID userId, String email, List<String> roles) {
        Instant now = Instant.now();
        long expirationSeconds = getExpirationSeconds();
        Instant expiry = now.plusSeconds(expirationSeconds);

        return Jwts.builder()
                .header()
                    .keyId(rsaKeyProvider.getKeyId())
                    .and()
                .subject(userId.toString())
                .claim("type", "user")
                .claim("email", email)
                .claim("roles", roles != null ? roles : List.of())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(rsaKeyProvider.getPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Issues an RS256-signed service-to-service JWT for an authenticated and trusted microservice caller.
     * Sets type="service", sub=serviceName, standard iat/exp claims with short TTL,
     * and explicitly excludes user-specific claims (email, roles).
     *
     * @param serviceName registered caller microservice identifier
     * @return compact RS256-signed service JWT
     * @throws UntrustedServiceException if serviceName is null, blank, or not in the trusted services registry
     */
    public String generateServiceToken(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            throw new UntrustedServiceException("Service name must not be null or blank");
        }

        String normalizedServiceName = serviceName.trim().toLowerCase();
        if (!TRUSTED_SERVICES.contains(normalizedServiceName)) {
            log.warn("Service token request rejected for untrusted service: '{}'", serviceName);
            throw new UntrustedServiceException("Untrusted or unrecognized service: '" + serviceName + "'");
        }

        Instant now = Instant.now();
        long expirationSeconds = getServiceTokenExpirationSeconds();
        Instant expiry = now.plusSeconds(expirationSeconds);

        return Jwts.builder()
                .header()
                    .keyId(rsaKeyProvider.getKeyId())
                    .and()
                .subject(normalizedServiceName)
                .claim("type", "service")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(rsaKeyProvider.getPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Parses and verifies an RS256-signed JWT against the configured public key.
     *
     * @param token the compact JWT string
     * @return parsed JWS containing header and claims payload
     */
    public Jws<Claims> parseAndValidateToken(String token) {
        return Jwts.parser()
                .verifyWith(rsaKeyProvider.getPublicKey())
                .build()
                .parseSignedClaims(token);
    }

    /**
     * Returns the token expiration duration in seconds (defaults to 1800 / 30 minutes).
     *
     * @return expiration in seconds
     */
    public long getExpirationSeconds() {
        return rsaKeyProperties.getExpirationSeconds() > 0
                ? rsaKeyProperties.getExpirationSeconds()
                : 1800L;
    }

    /**
     * Returns the service token expiration duration in seconds (defaults to 300 / 5 minutes).
     *
     * @return service expiration in seconds
     */
    public long getServiceTokenExpirationSeconds() {
        return rsaKeyProperties.getServiceTokenExpirationSeconds() > 0
                ? rsaKeyProperties.getServiceTokenExpirationSeconds()
                : 300L;
    }
}

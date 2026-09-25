package lk.ac.kelaniya.ams.identity_access_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.UnsupportedJwtException;
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
            "identity-access-service",
            "resident-management-service",
            "property-unit-service",
            "lease-occupancy-service",
            "billing-payment-service",
            "utility-charge-service",
            "operations-service",
            "community-service"
    );

    private final RsaKeyProvider rsaKeyProvider;
    private final RsaKeyProperties rsaKeyProperties;

    /**
     * Issues an RS256-signed JWT with subject (userId), type ("user"), roles, iat, exp (30 min expiry).
     * Strictly includes only permitted claims (sub, type, roles, iat, exp) and excludes kid header per JWT Standard Rule 3.
     *
     * @param userId unique user identifier
     * @param roles  list of assigned user roles
     * @return compact RS256-signed JWT string
     */
    public String generateToken(UUID userId, List<String> roles) {
        Instant now = Instant.now();
        long expirationSeconds = getExpirationSeconds();
        Instant expiry = now.plusSeconds(expirationSeconds);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("type", "user")
                .claim("roles", roles != null ? roles : List.of())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(rsaKeyProvider.getPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Overload preserving compatibility with existing callers. Email is omitted from JWT claims per JWT Standard Rule 3.
     *
     * @param userId unique user identifier
     * @param email  user email (ignored, not embedded in token)
     * @param roles  list of assigned user roles
     * @return compact RS256-signed JWT string
     */
    public String generateToken(UUID userId, String email, List<String> roles) {
        return generateToken(userId, roles);
    }

    /**
     * The fixed service identifier for Identity Access Service.
     */
    public static final String IDENTITY_ACCESS_SERVICE_NAME = "identity-access-service";

    /**
     * Issues an RS256-signed service-to-service JWT strictly for Identity Access Service's own outbound internal calls.
     * Signs a token asserting sub="identity-access-service", type="service", iat, exp.
     * Identity Access Service never acts as a centralized service-token issuer for other microservices per JWT Standard Rules 2, 5, 12.
     *
     * @return compact RS256-signed service JWT asserting sub="identity-access-service"
     */
    public String generateServiceToken() {
        return generateServiceToken(IDENTITY_ACCESS_SERVICE_NAME);
    }

    /**
     * Issues an RS256-signed service-to-service JWT strictly for Identity Access Service's own outbound calls.
     * Sets type="service", sub="identity-access-service", standard iat/exp claims with short TTL,
     * and strictly excludes user-specific claims (email, roles) and kid header per Rule 3.
     * Any attempt to mint a service token on behalf of another service is rejected per JWT Standard Rules 2, 5, and 12.
     *
     * @param serviceName caller microservice identifier (must strictly equal "identity-access-service")
     * @return compact RS256-signed service JWT
     * @throws UntrustedServiceException if serviceName is null, blank, or not "identity-access-service"
     */
    public String generateServiceToken(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            throw new UntrustedServiceException("Service name must not be null or blank");
        }

        String normalizedServiceName = serviceName.trim().toLowerCase();
        if (!IDENTITY_ACCESS_SERVICE_NAME.equals(normalizedServiceName)) {
            log.warn("Centralized service token issuance rejected for '{}'. Identity Access Service must not mint service tokens on behalf of other microservices. Each service must generate and sign its own Service JWT.", serviceName);
            throw new UntrustedServiceException("Identity Access Service cannot mint service tokens for other services: '" + serviceName + "'. Backend services must generate and sign their own Service JWTs.");
        }

        Instant now = Instant.now();
        long expirationSeconds = getServiceTokenExpirationSeconds();
        Instant expiry = now.plusSeconds(expirationSeconds);

        return Jwts.builder()
                .subject(IDENTITY_ACCESS_SERVICE_NAME)
                .claim("type", "service")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(rsaKeyProvider.getPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Parses and verifies an incoming RS256-signed JWT against the Gateway public key.
     * Enforces that the header algorithm is strictly RS256 before returning claims.
     *
     * @param token the compact JWT string
     * @return parsed JWS containing header and claims payload
     * @throws UnsupportedJwtException if token signing algorithm is not RS256
     */
    public Jws<Claims> parseAndValidateToken(String token) {
        Jws<Claims> claimsJws = Jwts.parser()
                .verifyWith(rsaKeyProvider.getGatewayPublicKey())
                .build()
                .parseSignedClaims(token);

        String algorithm = claimsJws.getHeader().getAlgorithm();
        if (!"RS256".equals(algorithm)) {
            log.warn("Rejected JWT with disallowed algorithm: '{}'. Only RS256 is permitted.", algorithm);
            throw new UnsupportedJwtException("Unsupported JWT algorithm: '" + algorithm + "'. Only RS256 is permitted.");
        }

        return claimsJws;
    }

    /**
     * Parses and verifies a User JWT signed by Identity Access Service against the service's own public key.
     * Used for validating tokens issued by this service at login.
     *
     * @param token the compact JWT string
     * @return parsed JWS containing header and claims payload
     * @throws UnsupportedJwtException if token signing algorithm is not RS256
     */
    public Jws<Claims> parseAndValidateUserToken(String token) {
        Jws<Claims> claimsJws = Jwts.parser()
                .verifyWith(rsaKeyProvider.getPublicKey())
                .build()
                .parseSignedClaims(token);

        String algorithm = claimsJws.getHeader().getAlgorithm();
        if (!"RS256".equals(algorithm)) {
            log.warn("Rejected JWT with disallowed algorithm: '{}'. Only RS256 is permitted.", algorithm);
            throw new UnsupportedJwtException("Unsupported JWT algorithm: '" + algorithm + "'. Only RS256 is permitted.");
        }

        return claimsJws;
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

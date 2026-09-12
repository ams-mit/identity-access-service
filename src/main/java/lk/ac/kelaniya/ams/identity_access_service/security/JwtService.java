package lk.ac.kelaniya.ams.identity_access_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Service responsible for issuing and verifying RS256-signed JSON Web Tokens (JWT).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private final RsaKeyProvider rsaKeyProvider;
    private final RsaKeyProperties rsaKeyProperties;

    /**
     * Issues an RS256-signed JWT with subject (userId), email, roles, iat, exp (30 min expiry),
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
                .claim("email", email)
                .claim("roles", roles != null ? roles : List.of())
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
}

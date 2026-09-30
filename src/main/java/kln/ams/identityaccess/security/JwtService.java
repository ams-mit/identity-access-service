package kln.ams.identityaccess.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.UnsupportedJwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    public static final String IDENTITY_ACCESS_SERVICE_NAME = "identity-access-service";

    private final RsaKeyProvider rsaKeyProvider;
    private final RsaKeyProperties rsaKeyProperties;

    public String generateToken(UUID userId, List<String> roles) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(rsaKeyProperties.getExpirationSeconds());

        return Jwts.builder()
                .subject(userId.toString())
                .claim("type", "user")
                .claim("roles", roles != null ? roles : List.of())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(rsaKeyProvider.getPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    public String generateServiceToken() {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(rsaKeyProperties.getServiceTokenExpirationSeconds());

        return Jwts.builder()
                .subject(IDENTITY_ACCESS_SERVICE_NAME)
                .claim("type", "service")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(rsaKeyProvider.getServicePrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    public Jws<Claims> parseAndValidateToken(String token) {
        Jws<Claims> claimsJws = Jwts.parser()
                .verifyWith(rsaKeyProvider.getGatewayPublicKey())
                .build()
                .parseSignedClaims(token);

        String algorithm = claimsJws.getHeader().getAlgorithm();
        if (!"RS256".equals(algorithm)) {
            log.warn("Rejected token with disallowed algorithm: '{}'. Only RS256 is permitted.", algorithm);
            throw new UnsupportedJwtException("Unsupported JWT algorithm: '" + algorithm + "'. Only RS256 is permitted.");
        }

        return claimsJws;
    }
}

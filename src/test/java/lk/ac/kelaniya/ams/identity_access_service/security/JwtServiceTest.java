package lk.ac.kelaniya.ams.identity_access_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.UnsupportedJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import javax.crypto.SecretKey;

import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    @TempDir
    Path tempDir;

    private RsaKeyProperties properties;
    private RsaKeyProvider rsaKeyProvider;
    private JwtService jwtService;

    @BeforeEach
    void setUp() throws Exception {
        properties = new RsaKeyProperties();
        properties.setKeyId("test-kid-123");
        properties.setExpirationSeconds(1800);

        KeyPair keyPair = RsaKeyPairGenerator.generateKeyPair(2048);
        Path privateKeyFile = tempDir.resolve("private_key.pem");
        Path publicKeyFile = tempDir.resolve("public_key.pem");
        RsaKeyPairGenerator.writeKeys(keyPair, privateKeyFile, publicKeyFile);

        properties.setPrivateKeyPath(privateKeyFile.toUri().toString());
        properties.setPublicKeyPath(publicKeyFile.toUri().toString());

        rsaKeyProvider = new RsaKeyProvider(properties, new DefaultResourceLoader());
        rsaKeyProvider.init();

        jwtService = new JwtService(rsaKeyProvider, properties);
    }

    @Test
    @DisplayName("Issued JWT has valid RS256 signature, no kid header, and strictly permitted claims (sub, type, roles, iat, exp)")
    void testGenerateToken_validSignatureAndClaims() {
        UUID userId = UUID.randomUUID();
        String email = "resident@ams.lk";
        List<String> roles = List.of("RESIDENT", "TENANT");

        String token = jwtService.generateToken(userId, email, roles);

        assertThat(token).isNotBlank();

        // Verify token signature and claims using the public key
        Jws<Claims> parsedJws = jwtService.parseAndValidateToken(token);

        // Header assertions: kid excluded per Rule 3
        assertThat(parsedJws.getHeader().getKeyId()).isNull();
        assertThat(parsedJws.getHeader().getAlgorithm()).isEqualTo("RS256");

        // Payload / Claims assertions: strictly sub, type, roles, iat, exp
        Claims payload = parsedJws.getPayload();
        assertThat(payload.keySet()).containsExactlyInAnyOrder("sub", "type", "roles", "iat", "exp");
        assertThat(payload.getSubject()).isEqualTo(userId.toString());
        assertThat(payload.get("type", String.class)).isEqualTo("user");
        assertThat(payload.get("email")).isNull();
        assertThat(payload.get("roles", List.class)).containsExactly("RESIDENT", "TENANT");

        Date iat = payload.getIssuedAt();
        Date exp = payload.getExpiration();
        assertThat(iat).isNotNull();
        assertThat(exp).isNotNull();

        long diffSeconds = (exp.getTime() - iat.getTime()) / 1000;
        assertThat(diffSeconds).isEqualTo(1800);
    }

    @Test
    @DisplayName("Issued JWT supports empty roles list and strictly permitted claims")
    void testGenerateToken_emptyRoles() {
        UUID userId = UUID.randomUUID();
        String email = "user@ams.lk";

        String token = jwtService.generateToken(userId, email, List.of());
        Jws<Claims> parsedJws = jwtService.parseAndValidateToken(token);

        Claims payload = parsedJws.getPayload();
        assertThat(payload.keySet()).containsExactlyInAnyOrder("sub", "type", "roles", "iat", "exp");
        assertThat(payload.getSubject()).isEqualTo(userId.toString());
        assertThat(payload.get("email")).isNull();
        assertThat(payload.get("roles", List.class)).isEmpty();
    }

    @Test
    @DisplayName("parseAndValidateToken throws ExpiredJwtException when token is expired")
    void testParseAndValidateToken_expiredToken_throwsExpiredJwtException() {
        Instant past = Instant.now().minusSeconds(3600);
        Instant pastExpiry = Instant.now().minusSeconds(1800);

        String expiredToken = io.jsonwebtoken.Jwts.builder()
                .header()
                    .keyId(rsaKeyProvider.getKeyId())
                    .and()
                .subject(UUID.randomUUID().toString())
                .claim("email", "expired@ams.lk")
                .claim("roles", List.of("RESIDENT"))
                .issuedAt(Date.from(past))
                .expiration(Date.from(pastExpiry))
                .signWith(rsaKeyProvider.getPrivateKey(), io.jsonwebtoken.Jwts.SIG.RS256)
                .compact();

        assertThatThrownBy(() -> jwtService.parseAndValidateToken(expiredToken))
                .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    }

    @Test
    @DisplayName("parseAndValidateToken throws SignatureException when token is signed with a different key")
    void testParseAndValidateToken_tokenSignedWithDifferentKey_throwsSignatureException() throws Exception {
        KeyPair otherKeyPair = RsaKeyPairGenerator.generateKeyPair(2048);

        String foreignToken = io.jsonwebtoken.Jwts.builder()
                .header()
                    .keyId("foreign-key-id")
                    .and()
                .subject(UUID.randomUUID().toString())
                .claim("email", "tampered@ams.lk")
                .claim("roles", List.of("RESIDENT"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 1800000))
                .signWith(otherKeyPair.getPrivate(), io.jsonwebtoken.Jwts.SIG.RS256)
                .compact();

        assertThatThrownBy(() -> jwtService.parseAndValidateToken(foreignToken))
                .isInstanceOf(io.jsonwebtoken.security.SignatureException.class);
    }

    @Test
    @DisplayName("parseAndValidateToken throws MalformedJwtException when token is not a valid JWT")
    void testParseAndValidateToken_malformedToken_throwsMalformedJwtException() {
        assertThatThrownBy(() -> jwtService.parseAndValidateToken("not.a.valid.jwt.payload"))
                .isInstanceOf(io.jsonwebtoken.MalformedJwtException.class);
    }

    @Test
    @DisplayName("parseAndValidateToken rejects token with HS256 algorithm (disallowed symmetric algorithm)")
    void testParseAndValidateToken_hs256Algorithm_throwsJwtException() {
        SecretKey hmacKey = io.jsonwebtoken.Jwts.SIG.HS256.key().build();
        String hs256Token = io.jsonwebtoken.Jwts.builder()
                .header()
                    .keyId("test-kid-123")
                    .and()
                .subject(UUID.randomUUID().toString())
                .claim("type", "user")
                .claim("email", "attacker@ams.lk")
                .claim("roles", List.of("SYSTEM_ADMINISTRATOR"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 1800000))
                .signWith(hmacKey, io.jsonwebtoken.Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> jwtService.parseAndValidateToken(hs256Token))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    @DisplayName("parseAndValidateToken rejects token with RS384 algorithm (only RS256 allowed)")
    void testParseAndValidateToken_rs384Algorithm_throwsUnsupportedJwtException() {
        String rs384Token = io.jsonwebtoken.Jwts.builder()
                .header()
                    .keyId("test-kid-123")
                    .and()
                .subject(UUID.randomUUID().toString())
                .claim("type", "user")
                .claim("email", "resident@ams.lk")
                .claim("roles", List.of("RESIDENT"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 1800000))
                .signWith(rsaKeyProvider.getPrivateKey(), io.jsonwebtoken.Jwts.SIG.RS384)
                .compact();

        assertThatThrownBy(() -> jwtService.parseAndValidateToken(rs384Token))
                .isInstanceOf(UnsupportedJwtException.class)
                .hasMessageContaining("Only RS256 is permitted");
    }

    @Test
    @DisplayName("generateServiceToken creates valid RS256 token with type=service and no email/roles")
    void testGenerateServiceToken_validTrustedService() {
        assertThat(JwtService.TRUSTED_SERVICES).containsExactlyInAnyOrder(
                "identity-access-service",
                "resident-management-service",
                "property-unit-service",
                "lease-occupancy-service",
                "billing-payment-service",
                "utility-charge-service",
                "operations-service",
                "community-service"
        );

        String serviceName = "billing-payment-service";

        String token = jwtService.generateServiceToken(serviceName);

        assertThat(token).isNotBlank();

        Jws<Claims> parsedJws = jwtService.parseAndValidateToken(token);

        assertThat(parsedJws.getHeader().getKeyId()).isNull();
        assertThat(parsedJws.getHeader().getAlgorithm()).isEqualTo("RS256");

        Claims payload = parsedJws.getPayload();
        assertThat(payload.keySet()).containsExactlyInAnyOrder("sub", "type", "iat", "exp");
        assertThat(payload.getSubject()).isEqualTo("billing-payment-service");
        assertThat(payload.get("type", String.class)).isEqualTo("service");
        assertThat(payload.get("email")).isNull();
        assertThat(payload.get("roles")).isNull();

        Date iat = payload.getIssuedAt();
        Date exp = payload.getExpiration();
        assertThat(iat).isNotNull();
        assertThat(exp).isNotNull();

        long diffSeconds = (exp.getTime() - iat.getTime()) / 1000;
        assertThat(diffSeconds).isEqualTo(300);
    }

    @Test
    @DisplayName("generateServiceToken rejects untrusted service names")
    void testGenerateServiceToken_untrustedService_throwsException() {
        assertThatThrownBy(() -> jwtService.generateServiceToken("unknown-malicious-service"))
                .isInstanceOf(lk.ac.kelaniya.ams.identity_access_service.exception.UntrustedServiceException.class)
                .hasMessageContaining("Untrusted or unrecognized service");

        assertThatThrownBy(() -> jwtService.generateServiceToken("billing-service"))
                .isInstanceOf(lk.ac.kelaniya.ams.identity_access_service.exception.UntrustedServiceException.class);

        assertThatThrownBy(() -> jwtService.generateServiceToken("api-gateway"))
                .isInstanceOf(lk.ac.kelaniya.ams.identity_access_service.exception.UntrustedServiceException.class);
    }

    @Test
    @DisplayName("generateServiceToken rejects null or blank service names")
    void testGenerateServiceToken_blankOrNullService_throwsException() {
        assertThatThrownBy(() -> jwtService.generateServiceToken(null))
                .isInstanceOf(lk.ac.kelaniya.ams.identity_access_service.exception.UntrustedServiceException.class);

        assertThatThrownBy(() -> jwtService.generateServiceToken("   "))
                .isInstanceOf(lk.ac.kelaniya.ams.identity_access_service.exception.UntrustedServiceException.class);
    }
}

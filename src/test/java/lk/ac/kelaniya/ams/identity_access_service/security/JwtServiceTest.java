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
    @DisplayName("generateServiceToken creates valid RS256 token for identity-access-service with type=service and strictly permitted claims")
    void testGenerateServiceToken_identityAccessService_succeeds() {
        String token = jwtService.generateServiceToken();

        assertThat(token).isNotBlank();

        Jws<Claims> parsedJws = jwtService.parseAndValidateToken(token);

        assertThat(parsedJws.getHeader().getKeyId()).isNull();
        assertThat(parsedJws.getHeader().getAlgorithm()).isEqualTo("RS256");

        Claims payload = parsedJws.getPayload();
        assertThat(payload.keySet()).containsExactlyInAnyOrder("sub", "type", "iat", "exp");
        assertThat(payload.getSubject()).isEqualTo("identity-access-service");
        assertThat(payload.get("type", String.class)).isEqualTo("service");
        assertThat(payload.get("email")).isNull();
        assertThat(payload.get("roles")).isNull();

        Date iat = payload.getIssuedAt();
        Date exp = payload.getExpiration();
        assertThat(iat).isNotNull();
        assertThat(exp).isNotNull();

        long diffSeconds = (exp.getTime() - iat.getTime()) / 1000;
        assertThat(diffSeconds).isEqualTo(300);

        // Explicit overload with serviceName
        String tokenWithName = jwtService.generateServiceToken("identity-access-service");
        assertThat(tokenWithName).isNotBlank();
    }

    @Test
    @DisplayName("generateServiceToken rejects minting tokens for external microservices (Rules 2, 5, 12)")
    void testGenerateServiceToken_otherServices_throwsException() {
        assertThatThrownBy(() -> jwtService.generateServiceToken("billing-payment-service"))
                .isInstanceOf(lk.ac.kelaniya.ams.identity_access_service.exception.UntrustedServiceException.class)
                .hasMessageContaining("Identity Access Service cannot mint service tokens for other services");

        assertThatThrownBy(() -> jwtService.generateServiceToken("resident-management-service"))
                .isInstanceOf(lk.ac.kelaniya.ams.identity_access_service.exception.UntrustedServiceException.class)
                .hasMessageContaining("Identity Access Service cannot mint service tokens for other services");

        assertThatThrownBy(() -> jwtService.generateServiceToken("unknown-malicious-service"))
                .isInstanceOf(lk.ac.kelaniya.ams.identity_access_service.exception.UntrustedServiceException.class)
                .hasMessageContaining("Identity Access Service cannot mint service tokens for other services");
    }

    @Test
    @DisplayName("generateServiceToken rejects null or blank service names")
    void testGenerateServiceToken_blankOrNullService_throwsException() {
        assertThatThrownBy(() -> jwtService.generateServiceToken(null))
                .isInstanceOf(lk.ac.kelaniya.ams.identity_access_service.exception.UntrustedServiceException.class);

        assertThatThrownBy(() -> jwtService.generateServiceToken("   "))
                .isInstanceOf(lk.ac.kelaniya.ams.identity_access_service.exception.UntrustedServiceException.class);
    }

    @Test
    @DisplayName("parseAndValidateToken validates token signed with Gateway private key when gatewayPublicKey is configured")
    void testParseAndValidateToken_withConfiguredGatewayPublicKey() throws Exception {
        KeyPair gwKeyPair = RsaKeyPairGenerator.generateKeyPair(2048);
        Path gwPubKeyFile = tempDir.resolve("gw_public_key.pem");
        Path gwPrivKeyFile = tempDir.resolve("gw_private_key.pem");
        RsaKeyPairGenerator.writeKeys(gwKeyPair, gwPrivKeyFile, gwPubKeyFile);

        properties.setGatewayPublicKeyPath(gwPubKeyFile.toUri().toString());
        rsaKeyProvider.init();

        // Mint token signed with gateway private key
        Instant now = Instant.now();
        String gatewayToken = io.jsonwebtoken.Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("type", "user")
                .claim("roles", List.of("RESIDENT"))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(1800)))
                .signWith(gwKeyPair.getPrivate(), io.jsonwebtoken.Jwts.SIG.RS256)
                .compact();

        Jws<Claims> parsed = jwtService.parseAndValidateToken(gatewayToken);
        assertThat(parsed.getPayload().get("type")).isEqualTo("user");

        // Token signed with Identity Access private key must fail parseAndValidateToken because filter/parse verifies ONLY with Gateway key
        String userTokenSignedByIdentity = jwtService.generateToken(UUID.randomUUID(), List.of("RESIDENT"));
        assertThatThrownBy(() -> jwtService.parseAndValidateToken(userTokenSignedByIdentity))
                .isInstanceOf(io.jsonwebtoken.security.SignatureException.class);

        // But user token signed by Identity Access validates via parseAndValidateUserToken
        Jws<Claims> parsedUserToken = jwtService.parseAndValidateUserToken(userTokenSignedByIdentity);
        assertThat(parsedUserToken.getPayload().get("type")).isEqualTo("user");
    }

    @Test
    @DisplayName("generateServiceToken uses servicePrivateKey when configured, keeping service signing key separate from user token key")
    void testGenerateServiceToken_withConfiguredServicePrivateKey() throws Exception {
        KeyPair svcKeyPair = RsaKeyPairGenerator.generateKeyPair(2048);
        Path svcPubKeyFile = tempDir.resolve("svc_public_key.pem");
        Path svcPrivKeyFile = tempDir.resolve("svc_private_key.pem");
        RsaKeyPairGenerator.writeKeys(svcKeyPair, svcPrivKeyFile, svcPubKeyFile);

        properties.setServicePrivateKeyPath(svcPrivKeyFile.toUri().toString());
        rsaKeyProvider.init();

        String serviceToken = jwtService.generateServiceToken();
        assertThat(serviceToken).isNotBlank();

        // Must verify against svcKeyPair.getPublic()
        Jws<Claims> parsed = io.jsonwebtoken.Jwts.parser()
                .verifyWith(svcKeyPair.getPublic())
                .build()
                .parseSignedClaims(serviceToken);
        assertThat(parsed.getPayload().getSubject()).isEqualTo("identity-access-service");
        assertThat(parsed.getPayload().get("type")).isEqualTo("service");

        // Must FAIL verification against user-token public key (rsaKeyProvider.getPublicKey())
        assertThatThrownBy(() -> io.jsonwebtoken.Jwts.parser()
                .verifyWith(rsaKeyProvider.getPublicKey())
                .build()
                .parseSignedClaims(serviceToken))
                .isInstanceOf(io.jsonwebtoken.security.SignatureException.class);

        // Meanwhile, user tokens still use user-token private key (not service private key)
        String userToken = jwtService.generateToken(UUID.randomUUID(), List.of("TENANT"));
        Jws<Claims> parsedUserToken = io.jsonwebtoken.Jwts.parser()
                .verifyWith(rsaKeyProvider.getPublicKey())
                .build()
                .parseSignedClaims(userToken);
        assertThat(parsedUserToken.getPayload().get("type")).isEqualTo("user");

        assertThatThrownBy(() -> io.jsonwebtoken.Jwts.parser()
                .verifyWith(svcKeyPair.getPublic())
                .build()
                .parseSignedClaims(userToken))
                .isInstanceOf(io.jsonwebtoken.security.SignatureException.class);
    }
}

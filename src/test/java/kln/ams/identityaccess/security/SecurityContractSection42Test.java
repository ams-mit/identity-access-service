package kln.ams.identityaccess.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security integration tests per Contract Section 42.3:
 * - missing Authorization header
 * - malformed Bearer token
 * - non-RS256 token (wrong JWT algorithm)
 * - invalid signature (untrusted key)
 * - expired token
 * - user token on internal endpoints (/api/v1/internal/** -> 401)
 * - service token on user endpoints (/api/v1/users -> 401)
 * - non-admin user on admin endpoint (/api/v1/users -> 403)
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect"
})
@AutoConfigureMockMvc
class SecurityContractSection42Test {

    @Autowired
    private MockMvc mockMvc;

    @MockBean(answer = RETURNS_DEEP_STUBS)
    private DataSource dataSource;

    @MockBean
    private Flyway flyway;

    private static KeyPair gatewayKeyPair;
    private static KeyPair serviceKeyPair;
    private static KeyPair attackerKeyPair;

    @DynamicPropertySource
    static void rsaKeyProperties(DynamicPropertyRegistry registry) throws Exception {
        Path tempDir = Files.createTempDirectory("ams-sec42-keys-");
        Path privateKeyFile = tempDir.resolve("private_key.pem");
        Path publicKeyFile = tempDir.resolve("public_key.pem");
        Path gatewayPublicKeyFile = tempDir.resolve("gateway_public_key.pem");
        Path servicePrivateKeyFile = tempDir.resolve("service_private_key.pem");

        gatewayKeyPair = RsaKeyPairGenerator.generateKeyPair(2048);
        serviceKeyPair = RsaKeyPairGenerator.generateKeyPair(2048);
        attackerKeyPair = RsaKeyPairGenerator.generateKeyPair(2048);

        RsaKeyPairGenerator.writeKeys(serviceKeyPair, privateKeyFile, publicKeyFile);
        RsaKeyPairGenerator.writeKeys(gatewayKeyPair, tempDir.resolve("unused_priv.pem"), gatewayPublicKeyFile);
        Files.writeString(servicePrivateKeyFile, RsaKeyPairGenerator.toPrivateKeyPem(serviceKeyPair));

        registry.add("jwt.private-key-path", () -> privateKeyFile.toUri().toString());
        registry.add("jwt.public-key-path", () -> publicKeyFile.toUri().toString());
        registry.add("jwt.gateway-public-key-path", () -> gatewayPublicKeyFile.toUri().toString());
        registry.add("jwt.service-private-key-path", () -> servicePrivateKeyFile.toUri().toString());
    }

    private String createUserToken(UUID userId, List<String> roles, KeyPair signingKeyPair, Instant expiry) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("type", "user")
                .claim("roles", roles)
                .issuedAt(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)))
                .expiration(Date.from(expiry))
                .signWith(signingKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private String createServiceToken(String serviceName, KeyPair signingKeyPair, Instant expiry) {
        return Jwts.builder()
                .subject(serviceName)
                .claim("type", "service")
                .issuedAt(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)))
                .expiration(Date.from(expiry))
                .signWith(signingKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    @Test
    void testMissingAuthorizationHeader_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    @Test
    void testMalformedBearerToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer this-is-not-a-valid-jwt-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    @Test
    void testNonRS256Token_Returns401() throws Exception {
        SecretKey secretKey = Keys.hmacShaKeyFor("a-very-long-secret-key-for-testing-purposes-only-32bytes".getBytes(StandardCharsets.UTF_8));
        String hs256Token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("type", "user")
                .claim("roles", List.of("SYSTEM_ADMINISTRATOR"))
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plus(30, ChronoUnit.MINUTES)))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + hs256Token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    @Test
    void testInvalidSignature_Returns401() throws Exception {
        String tokenSignedByAttacker = createUserToken(
                UUID.randomUUID(),
                List.of("SYSTEM_ADMINISTRATOR"),
                attackerKeyPair,
                Instant.now().plus(30, ChronoUnit.MINUTES)
        );

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + tokenSignedByAttacker))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    @Test
    void testExpiredToken_Returns401() throws Exception {
        String expiredToken = createUserToken(
                UUID.randomUUID(),
                List.of("SYSTEM_ADMINISTRATOR"),
                gatewayKeyPair,
                Instant.now().minus(10, ChronoUnit.MINUTES)
        );

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    @Test
    void testUserTokenOnInternalEndpoint_Returns401() throws Exception {
        UUID userId = UUID.randomUUID();
        String validUserToken = createUserToken(
                userId,
                List.of("SYSTEM_ADMINISTRATOR"),
                gatewayKeyPair,
                Instant.now().plus(30, ChronoUnit.MINUTES)
        );

        mockMvc.perform(get("/api/v1/internal/users/" + userId + "/validate")
                        .header("Authorization", "Bearer " + validUserToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_SERVICE_TOKEN"));
    }

    @Test
    void testMissingServiceTokenOnInternalEndpoint_Returns401() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/internal/users/" + userId + "/validate"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_SERVICE_TOKEN"));
    }

    @Test
    void testServiceTokenOnUserEndpoint_Returns401() throws Exception {
        String validServiceToken = createServiceToken(
                "resident-management-service",
                gatewayKeyPair,
                Instant.now().plus(5, ChronoUnit.MINUTES)
        );

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + validServiceToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    @Test
    void testNonAdminUserOnAdminEndpoint_Returns403() throws Exception {
        UUID userId = UUID.randomUUID();
        String nonAdminUserToken = createUserToken(
                userId,
                List.of("TENANT_RESIDENT"),
                gatewayKeyPair,
                Instant.now().plus(30, ChronoUnit.MINUTES)
        );

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + nonAdminUserToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
    }
}

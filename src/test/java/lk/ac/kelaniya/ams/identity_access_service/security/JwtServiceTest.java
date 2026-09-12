package lk.ac.kelaniya.ams.identity_access_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
    @DisplayName("Issued JWT has valid RS256 signature, kid header, and required claims")
    void testGenerateToken_validSignatureAndClaims() {
        UUID userId = UUID.randomUUID();
        String email = "resident@ams.lk";
        List<String> roles = List.of("RESIDENT", "TENANT");

        String token = jwtService.generateToken(userId, email, roles);

        assertThat(token).isNotBlank();

        // Verify token signature and claims using the public key
        Jws<Claims> parsedJws = jwtService.parseAndValidateToken(token);

        // Header assertions
        assertThat(parsedJws.getHeader().getKeyId()).isEqualTo("test-kid-123");
        assertThat(parsedJws.getHeader().getAlgorithm()).isEqualTo("RS256");

        // Payload / Claims assertions
        Claims payload = parsedJws.getPayload();
        assertThat(payload.getSubject()).isEqualTo(userId.toString());
        assertThat(payload.get("email", String.class)).isEqualTo("resident@ams.lk");
        assertThat(payload.get("roles", List.class)).containsExactly("RESIDENT", "TENANT");

        Date iat = payload.getIssuedAt();
        Date exp = payload.getExpiration();
        assertThat(iat).isNotNull();
        assertThat(exp).isNotNull();

        long diffSeconds = (exp.getTime() - iat.getTime()) / 1000;
        assertThat(diffSeconds).isEqualTo(1800);
    }

    @Test
    @DisplayName("Issued JWT supports empty roles list")
    void testGenerateToken_emptyRoles() {
        UUID userId = UUID.randomUUID();
        String email = "user@ams.lk";

        String token = jwtService.generateToken(userId, email, List.of());
        Jws<Claims> parsedJws = jwtService.parseAndValidateToken(token);

        Claims payload = parsedJws.getPayload();
        assertThat(payload.getSubject()).isEqualTo(userId.toString());
        assertThat(payload.get("email", String.class)).isEqualTo("user@ams.lk");
        assertThat(payload.get("roles", List.class)).isEmpty();
    }
}

package kln.ams.identityaccess.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.KeyPair;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @Mock
    private RsaKeyProvider rsaKeyProvider;

    private RsaKeyProperties rsaKeyProperties;
    private JwtService jwtService;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        keyPair = RsaKeyPairGenerator.generateKeyPair(2048);
        rsaKeyProperties = new RsaKeyProperties();
        rsaKeyProperties.setExpirationSeconds(1800);
        rsaKeyProperties.setServiceTokenExpirationSeconds(300);

        jwtService = new JwtService(rsaKeyProvider, rsaKeyProperties);
    }

    @Test
    void generateToken_ProducesCompliantUserJwt() {
        when(rsaKeyProvider.getPrivateKey()).thenReturn(keyPair.getPrivate());
        when(rsaKeyProvider.getGatewayPublicKey()).thenReturn(keyPair.getPublic());

        UUID userId = UUID.randomUUID();
        List<String> roles = List.of("TENANT_RESIDENT");

        String token = jwtService.generateToken(userId, roles);

        assertThat(token).isNotBlank();

        Jws<Claims> parsed = jwtService.parseAndValidateToken(token);
        assertThat(parsed.getHeader().getAlgorithm()).isEqualTo("RS256");
        assertThat(parsed.getPayload().getSubject()).isEqualTo(userId.toString());
        assertThat(parsed.getPayload().get("type", String.class)).isEqualTo("user");
        assertThat(parsed.getPayload().get("roles", List.class)).containsExactly("TENANT_RESIDENT");
        assertThat(parsed.getPayload().getIssuedAt()).isNotNull();
        assertThat(parsed.getPayload().getExpiration()).isNotNull();

        // Ensure iss and aud are removed per canonical contract
        assertThat(parsed.getPayload().getIssuer()).isNull();
        assertThat(parsed.getPayload().getAudience()).isNull();

        // Ensure forbidden claims are not present
        assertThat(parsed.getPayload().get("email")).isNull();
        assertThat(parsed.getPayload().get("permissions")).isNull();
        assertThat(parsed.getPayload().get("unitId")).isNull();
        assertThat(parsed.getHeader().getKeyId()).isNull();
    }

    @Test
    void generateServiceToken_ProducesCompliantServiceJwt() {
        when(rsaKeyProvider.getServicePrivateKey()).thenReturn(keyPair.getPrivate());
        when(rsaKeyProvider.getGatewayPublicKey()).thenReturn(keyPair.getPublic());

        String token = jwtService.generateServiceToken();

        assertThat(token).isNotBlank();

        Jws<Claims> parsed = jwtService.parseAndValidateToken(token);
        assertThat(parsed.getHeader().getAlgorithm()).isEqualTo("RS256");
        assertThat(parsed.getPayload().getSubject()).isEqualTo("identity-access-service");
        assertThat(parsed.getPayload().get("type", String.class)).isEqualTo("service");
        assertThat(parsed.getPayload().get("roles")).isNull();
        assertThat(parsed.getPayload().getIssuedAt()).isNotNull();
        assertThat(parsed.getPayload().getExpiration()).isNotNull();

        // Ensure iss and aud are removed per canonical contract
        assertThat(parsed.getPayload().getIssuer()).isNull();
        assertThat(parsed.getPayload().getAudience()).isNull();
    }
}

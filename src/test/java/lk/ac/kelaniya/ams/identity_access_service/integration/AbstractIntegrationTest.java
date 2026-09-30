package lk.ac.kelaniya.ams.identity_access_service.integration;

import lk.ac.kelaniya.ams.identity_access_service.dto.request.LoginRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.ApiResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.LoginResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lk.ac.kelaniya.ams.identity_access_service.entity.Role;
import lk.ac.kelaniya.ams.identity_access_service.entity.User;
import lk.ac.kelaniya.ams.identity_access_service.entity.UserRole;
import lk.ac.kelaniya.ams.identity_access_service.repository.PasswordResetTokenRepository;
import lk.ac.kelaniya.ams.identity_access_service.repository.RoleRepository;
import lk.ac.kelaniya.ams.identity_access_service.repository.UserRepository;
import lk.ac.kelaniya.ams.identity_access_service.repository.UserRoleRepository;
import lk.ac.kelaniya.ams.identity_access_service.security.RsaKeyPairGenerator;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base integration test class for end-to-end testing with a real MySQL database via Testcontainers.
 * Spins up a single static container shared across subclasses to avoid per-test startup overhead.
 * Configures Spring Boot to bind to the dynamic container JDBC properties and execute full real Flyway migrations.
 * Uses real TestRestTemplate over genuine HTTP with an embedded servlet container on a random port.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    protected static final MySQLContainer<?> MYSQL_CONTAINER = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("identity_db")
            .withUsername("identity_user")
            .withPassword("identity_pass");

    private static final Path TEMP_KEY_DIR;
    private static final Path TEMP_PRIVATE_KEY;
    private static final Path TEMP_PUBLIC_KEY;
    private static final Path TEMP_GATEWAY_PRIVATE_KEY;
    private static final Path TEMP_GATEWAY_PUBLIC_KEY;
    protected static final KeyPair IDENTITY_KEY_PAIR;
    protected static final KeyPair GATEWAY_KEY_PAIR;

    static {
        try {
            TEMP_KEY_DIR = Files.createTempDirectory("ams-test-rsa-keys-");
            TEMP_PRIVATE_KEY = TEMP_KEY_DIR.resolve("private_key.pem");
            TEMP_PUBLIC_KEY = TEMP_KEY_DIR.resolve("public_key.pem");
            IDENTITY_KEY_PAIR = RsaKeyPairGenerator.generateKeyPair(2048);
            RsaKeyPairGenerator.writeKeys(IDENTITY_KEY_PAIR, TEMP_PRIVATE_KEY, TEMP_PUBLIC_KEY);

            GATEWAY_KEY_PAIR = RsaKeyPairGenerator.generateKeyPair(2048);
            TEMP_GATEWAY_PRIVATE_KEY = TEMP_KEY_DIR.resolve("gateway_private_key.pem");
            TEMP_GATEWAY_PUBLIC_KEY = TEMP_KEY_DIR.resolve("gateway_public_key.pem");
            RsaKeyPairGenerator.writeKeys(GATEWAY_KEY_PAIR, TEMP_GATEWAY_PRIVATE_KEY, TEMP_GATEWAY_PUBLIC_KEY);

            TEMP_KEY_DIR.toFile().deleteOnExit();
            TEMP_PRIVATE_KEY.toFile().deleteOnExit();
            TEMP_PUBLIC_KEY.toFile().deleteOnExit();
            TEMP_GATEWAY_PRIVATE_KEY.toFile().deleteOnExit();
            TEMP_GATEWAY_PUBLIC_KEY.toFile().deleteOnExit();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate ephemeral RSA keypair for integration tests", e);
        }
        MYSQL_CONTAINER.start();
    }

    @DynamicPropertySource
    static void configureDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", MYSQL_CONTAINER::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL_CONTAINER::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");

        registry.add("jwt.private-key-path", () -> TEMP_PRIVATE_KEY.toUri().toString());
        registry.add("jwt.public-key-path", () -> TEMP_PUBLIC_KEY.toUri().toString());
        registry.add("jwt.gateway-public-key-path", () -> TEMP_GATEWAY_PUBLIC_KEY.toUri().toString());
        registry.add("jwt.service-private-key-path", () -> TEMP_PRIVATE_KEY.toUri().toString());

        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> "2525");
        registry.add("spring.mail.username", () -> "test-smtp-user");
        registry.add("spring.mail.password", () -> "test-smtp-password");
    }

    @MockBean
    protected JavaMailSender javaMailSender;

    @Autowired
    protected TestRestTemplate restTemplate;

    @jakarta.annotation.PostConstruct
    void configureClient() {
        restTemplate.getRestTemplate().setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory());
    }

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected RoleRepository roleRepository;

    @Autowired
    protected UserRoleRepository userRoleRepository;

    @Autowired
    protected PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired(required = false)
    protected lk.ac.kelaniya.ams.identity_access_service.security.ratelimit.RateLimitingService rateLimitingService;

    @AfterEach
    void cleanDatabase() {
        if (rateLimitingService != null) {
            rateLimitingService.reset();
        }
        passwordResetTokenRepository.deleteAll();
        userRoleRepository.deleteAll();
        userRepository.deleteAll();
    }

    /**
     * Seeds an administrator user directly into the database with SYSTEM_ADMINISTRATOR role.
     */
    protected User seedAdminUser(String email, String rawPassword) {
        return seedUserWithRole(email, rawPassword, "SYSTEM_ADMINISTRATOR", AccountStatus.ACTIVE);
    }

    /**
     * Seeds a user directly into the database with a specific role and status.
     */
    protected User seedUserWithRole(String email, String rawPassword, String roleName, AccountStatus status) {
        Role role = roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .id(UUID.randomUUID())
                        .name(roleName)
                        .description(roleName + " role")
                        .build()));

        User user = User.builder()
                .email(email.toLowerCase())
                .username(email.toLowerCase())
                .passwordHash(passwordEncoder.encode(rawPassword))
                .firstName("Test")
                .lastName("User")
                .accountStatus(status)
                .mustChangePassword(false)
                .failedAttemptCount(0)
                .userRoles(new HashSet<>())
                .build();

        user = userRepository.save(user);
        user.addRole(role);
        return userRepository.save(user);
    }

    /**
     * Executes real HTTP login and returns the raw access token signed with Identity Access private key.
     */
    protected String loginAndGetRawToken(String email, String password) {
        LoginRequest loginRequest = LoginRequest.builder()
                .email(email)
                .password(password)
                .build();

        ResponseEntity<ApiResponse<LoginResponse>> response = restTemplate.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(loginRequest),
                new ParameterizedTypeReference<ApiResponse<LoginResponse>>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isNotNull();
        assertThat(response.getBody().getData().getAccessToken()).isNotBlank();

        return response.getBody().getData().getAccessToken();
    }

    /**
     * Re-signs a User JWT with the test Gateway private key, simulating the API Gateway
     * validating the login token with Identity Access public key and forwarding a Gateway-signed token.
     */
    protected String reSignWithGatewayKey(String rawUserToken) {
        io.jsonwebtoken.Jws<io.jsonwebtoken.Claims> claimsJws = io.jsonwebtoken.Jwts.parser()
                .verifyWith(IDENTITY_KEY_PAIR.getPublic())
                .build()
                .parseSignedClaims(rawUserToken);
        io.jsonwebtoken.Claims claims = claimsJws.getPayload();

        return io.jsonwebtoken.Jwts.builder()
                .subject(claims.getSubject())
                .claim("type", claims.get("type"))
                .claim("roles", claims.get("roles"))
                .issuedAt(claims.getIssuedAt())
                .expiration(claims.getExpiration())
                .signWith(GATEWAY_KEY_PAIR.getPrivate(), io.jsonwebtoken.Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Executes real HTTP login and returns the Gateway-signed JWT access token string
     * representing the incoming Bearer token forwarding through the API Gateway.
     */
    protected String loginAndGetToken(String email, String password) {
        String rawToken = loginAndGetRawToken(email, password);
        return reSignWithGatewayKey(rawToken);
    }

    /**
     * Creates an incoming user token signed by the Gateway test private key.
     */
    protected String createGatewayUserToken(UUID userId, List<String> roles) {
        java.time.Instant now = java.time.Instant.now();
        return io.jsonwebtoken.Jwts.builder()
                .subject(userId.toString())
                .claim("type", "user")
                .claim("roles", roles != null ? roles : List.of())
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plusSeconds(1800)))
                .signWith(GATEWAY_KEY_PAIR.getPrivate(), io.jsonwebtoken.Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Creates an incoming service token signed by the Gateway test private key.
     */
    protected String createGatewayServiceToken(String serviceName) {
        java.time.Instant now = java.time.Instant.now();
        return io.jsonwebtoken.Jwts.builder()
                .subject(serviceName)
                .claim("type", "service")
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plusSeconds(300)))
                .signWith(GATEWAY_KEY_PAIR.getPrivate(), io.jsonwebtoken.Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Builds HTTP headers with a Bearer token authorization header.
     */
    protected HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    /**
     * Wraps payload and Bearer token into an HttpEntity.
     */
    protected <T> HttpEntity<T> createAuthEntity(T body, String token) {
        return new HttpEntity<>(body, authHeaders(token));
    }
}

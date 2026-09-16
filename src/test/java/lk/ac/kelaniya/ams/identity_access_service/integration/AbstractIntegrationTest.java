package lk.ac.kelaniya.ams.identity_access_service.integration;

import lk.ac.kelaniya.ams.identity_access_service.dto.request.LoginRequest;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    static {
        try {
            TEMP_KEY_DIR = Files.createTempDirectory("ams-test-rsa-keys-");
            TEMP_PRIVATE_KEY = TEMP_KEY_DIR.resolve("private_key.pem");
            TEMP_PUBLIC_KEY = TEMP_KEY_DIR.resolve("public_key.pem");
            KeyPair keyPair = RsaKeyPairGenerator.generateKeyPair(2048);
            RsaKeyPairGenerator.writeKeys(keyPair, TEMP_PRIVATE_KEY, TEMP_PUBLIC_KEY);
            TEMP_KEY_DIR.toFile().deleteOnExit();
            TEMP_PRIVATE_KEY.toFile().deleteOnExit();
            TEMP_PUBLIC_KEY.toFile().deleteOnExit();
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
    }

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

    @AfterEach
    void cleanDatabase() {
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
     * Executes real HTTP login and returns the parsed JWT access token string.
     */
    protected String loginAndGetToken(String email, String password) {
        LoginRequest loginRequest = LoginRequest.builder()
                .email(email)
                .password(password)
                .build();

        ResponseEntity<LoginResponse> response = restTemplate.postForEntity(
                "/api/v1/auth/login",
                loginRequest,
                LoginResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAccessToken()).isNotBlank();

        return response.getBody().getAccessToken();
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

package kln.ams.identityaccess;

import kln.ams.identityaccess.security.RsaKeyPairGenerator;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect"
})
@AutoConfigureMockMvc
class IdentityAccessServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private DataSource dataSource;

    @MockBean
    private Flyway flyway;

    @DynamicPropertySource
    static void rsaKeyProperties(DynamicPropertyRegistry registry) throws Exception {
        Path tempDir = Files.createTempDirectory("ams-test-keys-");
        Path privateKeyFile = tempDir.resolve("private_key.pem");
        Path publicKeyFile = tempDir.resolve("public_key.pem");
        KeyPair keyPair = RsaKeyPairGenerator.generateKeyPair(2048);
        RsaKeyPairGenerator.writeKeys(keyPair, privateKeyFile, publicKeyFile);

        registry.add("jwt.private-key-path", () -> privateKeyFile.toUri().toString());
        registry.add("jwt.public-key-path", () -> publicKeyFile.toUri().toString());
        registry.add("jwt.gateway-public-key-path", () -> publicKeyFile.toUri().toString());
        registry.add("jwt.service-private-key-path", () -> privateKeyFile.toUri().toString());
    }

    @Test
    void contextLoads() {
    }

    @Test
    void testOpenApiDocsGeneratesSuccessfully() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("Project A — Identity Access Service API"))
                .andExpect(jsonPath("$.tags[?(@.name == 'Authentication')]").exists())
                .andExpect(jsonPath("$.tags[?(@.name == 'User Management')]").exists())
                .andExpect(jsonPath("$.tags[?(@.name == 'Role Management')]").exists())
                .andExpect(jsonPath("$.tags[?(@.name == 'Permission Management')]").exists())
                .andExpect(jsonPath("$.tags[?(@.name == 'Internal User Provider APIs')]").exists());
    }
}

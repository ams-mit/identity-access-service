package lk.ac.kelaniya.ams.identity_access_service;

import lk.ac.kelaniya.ams.identity_access_service.security.RsaKeyPairGenerator;
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
		"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect"
})
@AutoConfigureMockMvc
class IdentityAccessServiceApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private DataSource dataSource;

	@MockBean
	private Flyway flyway;

	@MockBean
	private org.springframework.mail.javamail.JavaMailSender javaMailSender;

	@DynamicPropertySource
	static void rsaKeyProperties(DynamicPropertyRegistry registry) throws Exception {
		Path tempDir = Files.createTempDirectory("ams-test-keys-");
		Path privateKeyFile = tempDir.resolve("private_key.pem");
		Path publicKeyFile = tempDir.resolve("public_key.pem");
		KeyPair keyPair = RsaKeyPairGenerator.generateKeyPair(2048);
		RsaKeyPairGenerator.writeKeys(keyPair, privateKeyFile, publicKeyFile);

		registry.add("jwt.private-key-path", () -> privateKeyFile.toUri().toString());
		registry.add("jwt.public-key-path", () -> publicKeyFile.toUri().toString());

		registry.add("spring.mail.host", () -> "localhost");
		registry.add("spring.mail.port", () -> "2525");
		registry.add("spring.mail.username", () -> "test-smtp-user");
		registry.add("spring.mail.password", () -> "test-smtp-password");
	}

	@Test
	void contextLoads() {
	}

	@Test
	void testOpenApiDocsGeneratesSuccessfully() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.openapi").exists())
				.andExpect(jsonPath("$.info.version").value("1.0.0"))
				.andExpect(jsonPath("$.tags[?(@.name == 'User Profile')]").exists())
				.andExpect(jsonPath("$.tags[?(@.name == 'Admin - Roles')]").exists())
				.andExpect(jsonPath("$.tags[?(@.name == 'Public Key / JWKS')]").exists())
				.andExpect(jsonPath("$.tags[?(@.name == 'Internal - Service to Service')]").exists())
				.andExpect(jsonPath("$.tags[?(@.name == 'Audit Logs')]").exists());
	}

}

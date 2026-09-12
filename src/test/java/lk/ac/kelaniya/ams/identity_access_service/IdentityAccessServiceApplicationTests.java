package lk.ac.kelaniya.ams.identity_access_service;

import lk.ac.kelaniya.ams.identity_access_service.security.RsaKeyPairGenerator;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;

@SpringBootTest(properties = {
		"spring.flyway.enabled=false",
		"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect"
})
class IdentityAccessServiceApplicationTests {

	@MockBean
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
	}

	@Test
	void contextLoads() {
	}

}

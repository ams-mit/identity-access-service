package lk.ac.kelaniya.ams.identity_access_service.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Path;
import java.security.KeyPair;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Boot context test verifying fail-fast startup behavior when required
 * JWT key environment variables/properties are not supplied.
 */
class JwtKeyStartupValidationTest {

    @TempDir
    Path tempDir;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(RsaKeyProperties.class, RsaKeyProvider.class);

    @Test
    @DisplayName("Application context fails to start with clear error when JWT_PRIVATE_KEY_PATH is unset in docker profile")
    void testApplicationContextStartupFailsWhenPrivateKeyPathUnsetInDockerProfile() {
        contextRunner
                .withPropertyValues("spring.profiles.active=docker")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class);
                    assertThat(context.getStartupFailure().getCause())
                            .hasMessageContaining("RSA private key path is not configured. Please set 'jwt.private-key-path' or environment variable JWT_PRIVATE_KEY_PATH.");
                });
    }

    @Test
    @DisplayName("Application context fails to start with clear error when JWT_PUBLIC_KEY_PATH is unset in docker profile")
    void testApplicationContextStartupFailsWhenPublicKeyPathUnsetInDockerProfile() throws Exception {
        KeyPair keyPair = RsaKeyPairGenerator.generateKeyPair(2048);
        Path privateKeyFile = tempDir.resolve("private_key.pem");
        Path dummyPublicKey = tempDir.resolve("public_key.pem");
        RsaKeyPairGenerator.writeKeys(keyPair, privateKeyFile, dummyPublicKey);

        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=docker",
                        "jwt.private-key-path=" + privateKeyFile.toUri().toString()
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class);
                    assertThat(context.getStartupFailure().getCause())
                            .hasMessageContaining("RSA public key path is not configured. Please set 'jwt.public-key-path' or environment variable JWT_PUBLIC_KEY_PATH.");
                });
    }
}

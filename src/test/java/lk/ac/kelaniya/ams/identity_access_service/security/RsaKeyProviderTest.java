package lk.ac.kelaniya.ams.identity_access_service.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RsaKeyProviderTest {

    @TempDir
    Path tempDir;

    private RsaKeyProperties properties;
    private RsaKeyProvider provider;
    private Path privateKeyFile;
    private Path publicKeyFile;
    private Path gatewayPublicKeyFile;
    private Path servicePrivateKeyFile;

    @BeforeEach
    void setUp() throws Exception {
        properties = new RsaKeyProperties();
        provider = new RsaKeyProvider(properties, new DefaultResourceLoader());

        KeyPair keyPair = RsaKeyPairGenerator.generateKeyPair(2048);
        privateKeyFile = tempDir.resolve("private_key.pem");
        publicKeyFile = tempDir.resolve("public_key.pem");
        RsaKeyPairGenerator.writeKeys(keyPair, privateKeyFile, publicKeyFile);

        KeyPair gwKeyPair = RsaKeyPairGenerator.generateKeyPair(2048);
        Path gwPrivKeyFile = tempDir.resolve("gw_priv.pem");
        gatewayPublicKeyFile = tempDir.resolve("gw_pub.pem");
        RsaKeyPairGenerator.writeKeys(gwKeyPair, gwPrivKeyFile, gatewayPublicKeyFile);

        KeyPair svcKeyPair = RsaKeyPairGenerator.generateKeyPair(2048);
        servicePrivateKeyFile = tempDir.resolve("svc_priv.pem");
        Path svcPubKeyFile = tempDir.resolve("svc_pub.pem");
        RsaKeyPairGenerator.writeKeys(svcKeyPair, servicePrivateKeyFile, svcPubKeyFile);
    }

    @Test
    @DisplayName("Should successfully load and validate RSA keys when valid PEM files exist")
    void testLoadKeys_success() {
        properties.setPrivateKeyPath(privateKeyFile.toUri().toString());
        properties.setPublicKeyPath(publicKeyFile.toUri().toString());
        properties.setGatewayPublicKeyPath(gatewayPublicKeyFile.toUri().toString());
        properties.setServicePrivateKeyPath(servicePrivateKeyFile.toUri().toString());
        properties.setKeyId("test-key-id");

        provider.init();

        assertThat(provider.getPrivateKey()).isNotNull().isInstanceOf(RSAPrivateKey.class);
        assertThat(provider.getPublicKey()).isNotNull().isInstanceOf(RSAPublicKey.class);
        assertThat(provider.getKeyId()).isEqualTo("test-key-id");
        assertThat(provider.getPublicKeyPem())
                .startsWith("-----BEGIN PUBLIC KEY-----")
                .endsWith("-----END PUBLIC KEY-----\n");
    }

    @Test
    @DisplayName("Should fail fast with IllegalStateException when private key path is not configured")
    void testMissingPrivateKeyPath_failsFast() {
        properties.setPrivateKeyPath(null);
        properties.setPublicKeyPath(publicKeyFile.toUri().toString());

        assertThatThrownBy(() -> provider.init())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RSA private key path is not configured");
    }

    @Test
    @DisplayName("Should fail fast with IllegalStateException when private key file does not exist")
    void testNonExistentPrivateKeyFile_failsFast() {
        properties.setPrivateKeyPath(tempDir.resolve("does_not_exist.pem").toUri().toString());
        properties.setPublicKeyPath(publicKeyFile.toUri().toString());

        assertThatThrownBy(() -> provider.init())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RSA private key file not found");
    }

    @Test
    @DisplayName("Should fail fast with IllegalStateException when public key path is not configured")
    void testMissingPublicKeyPath_failsFast() {
        properties.setPrivateKeyPath(privateKeyFile.toUri().toString());
        properties.setPublicKeyPath(null);

        assertThatThrownBy(() -> provider.init())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RSA public key path is not configured");
    }

    @Test
    @DisplayName("Should fail fast with IllegalStateException when public key file does not exist")
    void testNonExistentPublicKeyFile_failsFast() {
        properties.setPrivateKeyPath(privateKeyFile.toUri().toString());
        properties.setPublicKeyPath(tempDir.resolve("missing_public.pem").toUri().toString());

        assertThatThrownBy(() -> provider.init())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RSA public key file not found");
    }

    @Test
    @DisplayName("Should fail fast with IllegalStateException when key PEM content is malformed")
    void testMalformedKeyPem_failsFast() throws Exception {
        Path badPem = tempDir.resolve("bad_key.pem");
        Files.writeString(badPem, "-----BEGIN PRIVATE KEY-----\nNOT_VALID_BASE64_KEY_DATA\n-----END PRIVATE KEY-----\n");

        properties.setPrivateKeyPath(badPem.toUri().toString());
        properties.setPublicKeyPath(publicKeyFile.toUri().toString());

        assertThatThrownBy(() -> provider.init())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to parse RSA private key");
    }

    @Test
    @DisplayName("Application context fails to start with clear error when RSA keys are missing")
    void testApplicationContextStartupFailsWhenKeysMissing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(RsaKeyProperties.class, RsaKeyProvider.class)
                .withPropertyValues(
                        "jwt.private-key-path=file:non_existent_path/private.pem",
                        "jwt.public-key-path=file:non_existent_path/public.pem"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class);
                    assertThat(context.getStartupFailure().getCause())
                            .hasMessageContaining("RSA private key");
                });
    }

    @Test
    @DisplayName("Application context fails to start with clear error when private key path is completely unset in non-local profile")
    void testApplicationContextStartupFailsWhenPrivateKeyPathUnsetInNonLocalProfile() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(RsaKeyProperties.class, RsaKeyProvider.class)
                .withPropertyValues("spring.profiles.active=docker")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class);
                    assertThat(context.getStartupFailure().getCause())
                            .hasMessageContaining("RSA private key path is not configured");
                });
    }

    @Test
    @DisplayName("Application context fails to start with clear error when public key path is completely unset in non-local profile")
    void testApplicationContextStartupFailsWhenPublicKeyPathUnsetInNonLocalProfile() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(RsaKeyProperties.class, RsaKeyProvider.class)
                .withPropertyValues(
                        "spring.profiles.active=docker",
                        "jwt.private-key-path=" + privateKeyFile.toUri().toString()
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class);
                    assertThat(context.getStartupFailure().getCause())
                            .hasMessageContaining("RSA public key path is not configured");
                });
    }

    @Test
    @DisplayName("Should load RSA keypair from externally-supplied file: URI path")
    void testLoadKeys_externalFilePath() {
        properties.setPrivateKeyPath("file:" + privateKeyFile.toAbsolutePath().toString().replace('\\', '/'));
        properties.setPublicKeyPath("file:" + publicKeyFile.toAbsolutePath().toString().replace('\\', '/'));
        properties.setGatewayPublicKeyPath("file:" + gatewayPublicKeyFile.toAbsolutePath().toString().replace('\\', '/'));
        properties.setServicePrivateKeyPath("file:" + servicePrivateKeyFile.toAbsolutePath().toString().replace('\\', '/'));
        properties.setKeyId("external-file-kid");

        provider.init();

        assertThat(provider.getPrivateKey()).isNotNull().isInstanceOf(RSAPrivateKey.class);
        assertThat(provider.getPublicKey()).isNotNull().isInstanceOf(RSAPublicKey.class);
        assertThat(provider.getKeyId()).isEqualTo("external-file-kid");
    }

    @Test
    @DisplayName("Should fail fast with IllegalStateException when gateway public key is not configured")
    void testMissingGatewayPublicKey_failsFast() {
        properties.setPrivateKeyPath(privateKeyFile.toUri().toString());
        properties.setPublicKeyPath(publicKeyFile.toUri().toString());
        properties.setGatewayPublicKeyPath(null);
        properties.setGatewayPublicKey(null);
        properties.setServicePrivateKeyPath(servicePrivateKeyFile.toUri().toString());

        assertThatThrownBy(() -> provider.init())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GATEWAY_JWT_PUBLIC_KEY");
    }

    @Test
    @DisplayName("Should fail fast with IllegalStateException when service private key is not configured")
    void testMissingServicePrivateKey_failsFast() {
        properties.setPrivateKeyPath(privateKeyFile.toUri().toString());
        properties.setPublicKeyPath(publicKeyFile.toUri().toString());
        properties.setGatewayPublicKeyPath(gatewayPublicKeyFile.toUri().toString());
        properties.setServicePrivateKeyPath(null);
        properties.setServicePrivateKey(null);

        assertThatThrownBy(() -> provider.init())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SERVICE_JWT_PRIVATE_KEY");
    }

    @Test
    @DisplayName("Should load distinct gateway public key and service private key when configured")
    void testLoadGatewayAndServiceKeys_distinctFromUserKeys() {
        properties.setPrivateKeyPath(privateKeyFile.toUri().toString());
        properties.setPublicKeyPath(publicKeyFile.toUri().toString());
        properties.setGatewayPublicKeyPath(gatewayPublicKeyFile.toUri().toString());
        properties.setServicePrivateKeyPath(servicePrivateKeyFile.toUri().toString());

        provider.init();

        assertThat(provider.getGatewayPublicKey()).isNotNull();
        assertThat(provider.getGatewayPublicKey()).isNotEqualTo(provider.getPublicKey());
        assertThat(provider.getServicePrivateKey()).isNotNull();
        assertThat(provider.getServicePrivateKey()).isNotEqualTo(provider.getPrivateKey());
    }
}

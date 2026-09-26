package lk.ac.kelaniya.ams.identity_access_service.security;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Loads, parses, and validates the RSA key pair at application startup for RS256 token signing/verification.
 * Fails fast with clear IllegalStateException if keys are missing or malformed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RsaKeyProvider {

    private static final String PRIVATE_KEY_HEADER = "-----BEGIN PRIVATE KEY-----";
    private static final String PRIVATE_KEY_FOOTER = "-----END PRIVATE KEY-----";
    private static final String PUBLIC_KEY_HEADER = "-----BEGIN PUBLIC KEY-----";
    private static final String PUBLIC_KEY_FOOTER = "-----END PUBLIC KEY-----";

    private final RsaKeyProperties properties;
    private final ResourceLoader resourceLoader;

    @Getter
    private RSAPrivateKey privateKey;

    @Getter
    private RSAPublicKey publicKey;

    @Getter
    private RSAPublicKey gatewayPublicKey;

    @Getter
    private RSAPrivateKey servicePrivateKey;

    @Getter
    private String keyId;

    @PostConstruct
    public void init() {
        this.keyId = (properties.getKeyId() != null && !properties.getKeyId().isBlank())
                ? properties.getKeyId()
                : "ams-identity-key-1";

        log.info("Initializing RSA key provider with kid: {}", this.keyId);

        this.privateKey = loadPrivateKey(properties.getPrivateKeyPath());
        this.publicKey = loadPublicKey(properties.getPublicKeyPath());

        String gwKeyConfig = properties.getGatewayPublicKeyPath();
        if (gwKeyConfig == null || gwKeyConfig.isBlank()) {
            gwKeyConfig = properties.getGatewayPublicKey();
        }
        if (gwKeyConfig == null || gwKeyConfig.isBlank()) {
            throw new IllegalStateException(
                    "Gateway public key is not configured. Please set 'jwt.gateway-public-key-path' or environment variable GATEWAY_JWT_PUBLIC_KEY.");
        }
        this.gatewayPublicKey = loadPublicKey(gwKeyConfig);
        log.info("Gateway public key successfully loaded and validated for incoming token verification.");

        String svcKeyConfig = properties.getServicePrivateKeyPath();
        if (svcKeyConfig == null || svcKeyConfig.isBlank()) {
            svcKeyConfig = properties.getServicePrivateKey();
        }
        if (svcKeyConfig == null || svcKeyConfig.isBlank()) {
            throw new IllegalStateException(
                    "Service private key is not configured. Please set 'jwt.service-private-key-path' or environment variable SERVICE_JWT_PRIVATE_KEY.");
        }
        this.servicePrivateKey = loadPrivateKey(svcKeyConfig);
        log.info("Service private key successfully loaded and validated for service token signing.");

        log.info("RSA key pair successfully loaded and validated for RS256 signing and verification.");
    }

    private RSAPrivateKey loadPrivateKey(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalStateException(
                    "RSA private key path is not configured. Please set 'jwt.private-key-path' or environment variable JWT_PRIVATE_KEY_PATH.");
        }

        try {
            if (path.contains(PRIVATE_KEY_HEADER)) {
                byte[] keyBytes = extractPemBytes(path, PRIVATE_KEY_HEADER, PRIVATE_KEY_FOOTER);
                PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                return (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
            }

            Resource resource = resolveResource(path);
            if (resource == null || !resource.exists()) {
                throw new IllegalStateException(
                        "RSA private key file not found at: '" + path + "'. Please ensure the file exists or run RsaKeyPairGenerator to generate keys.");
            }

            try (InputStream is = resource.getInputStream()) {
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                byte[] keyBytes = extractPemBytes(content, PRIVATE_KEY_HEADER, PRIVATE_KEY_FOOTER);
                PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                return (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to parse RSA private key from: '" + path + "'. Ensure it is in PKCS#8 PEM format. Details: " + e.getMessage(), e);
        }
    }

    private RSAPublicKey loadPublicKey(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalStateException(
                    "RSA public key path is not configured. Please set 'jwt.public-key-path' or environment variable JWT_PUBLIC_KEY_PATH.");
        }

        try {
            if (path.contains(PUBLIC_KEY_HEADER)) {
                byte[] keyBytes = extractPemBytes(path, PUBLIC_KEY_HEADER, PUBLIC_KEY_FOOTER);
                X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                return (RSAPublicKey) keyFactory.generatePublic(keySpec);
            }

            Resource resource = resolveResource(path);
            if (resource == null || !resource.exists()) {
                throw new IllegalStateException(
                        "RSA public key file not found at: '" + path + "'. Please ensure the file exists or run RsaKeyPairGenerator to generate keys.");
            }

            try (InputStream is = resource.getInputStream()) {
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                byte[] keyBytes = extractPemBytes(content, PUBLIC_KEY_HEADER, PUBLIC_KEY_FOOTER);
                X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                return (RSAPublicKey) keyFactory.generatePublic(keySpec);
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to parse RSA public key from: '" + path + "'. Ensure it is in X.509 PEM format. Details: " + e.getMessage(), e);
        }
    }

    private Resource resolveResource(String path) {
        Resource resource = resourceLoader.getResource(path);
        if (!resource.exists() && !path.startsWith("classpath:") && !path.startsWith("file:")) {
            Resource fileResource = resourceLoader.getResource("file:" + path);
            if (fileResource.exists()) {
                return fileResource;
            }
        }
        return resource;
    }

    private byte[] extractPemBytes(String pemContent, String header, String footer) {
        if (!pemContent.contains(header) || !pemContent.contains(footer)) {
            throw new IllegalArgumentException("PEM content does not contain expected header/footer: " + header + " ... " + footer);
        }

        String cleaned = pemContent
                .replace(header, "")
                .replace(footer, "")
                .replaceAll("\\s+", "");

        return Base64.getDecoder().decode(cleaned);
    }

    /**
     * Returns the public key encoded in standard X.509 PEM format.
     */
    public String getPublicKeyPem() {
        if (publicKey == null) {
            return null;
        }
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(publicKey.getEncoded());
        return PUBLIC_KEY_HEADER + "\n" + base64 + "\n" + PUBLIC_KEY_FOOTER + "\n";
    }
}

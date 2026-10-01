package kln.ams.identityaccess.security;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Slf4j
@Getter
@Component
@RequiredArgsConstructor
public class RsaKeyProvider {

    private final RsaKeyProperties properties;
    private final ResourceLoader resourceLoader = new DefaultResourceLoader();

    private PrivateKey privateKey;
    private PublicKey publicKey;
    private PublicKey gatewayPublicKey;
    private PrivateKey servicePrivateKey;

    @PostConstruct
    public void init() {
        log.info("Initializing RS256 cryptographic keys...");

        // 1. User signing private key (Identity Access private key)
        String privConfig = properties.getPrivateKeyPath();
        if (privConfig == null || privConfig.isBlank()) {
            privConfig = properties.getPrivateKey();
        }
        if (privConfig == null || privConfig.isBlank()) {
            throw new IllegalStateException("JWT private key is missing. Set JWT_PRIVATE_KEY or JWT_PRIVATE_KEY_PATH.");
        }
        this.privateKey = loadPrivateKey(privConfig);
        log.info("Identity Access private key loaded successfully.");

        // 2. Identity Access public key
        String pubConfig = properties.getPublicKeyPath();
        if (pubConfig == null || pubConfig.isBlank()) {
            pubConfig = properties.getPublicKey();
        }
        if (pubConfig != null && !pubConfig.isBlank()) {
            this.publicKey = loadPublicKey(pubConfig);
            log.info("Identity Access public key loaded successfully.");
        }

        // 3. Gateway public key (Strictly required for verifying incoming Gateway JWTs)
        String gwPubConfig = properties.getGatewayPublicKeyPath();
        if (gwPubConfig == null || gwPubConfig.isBlank()) {
            gwPubConfig = properties.getGatewayPublicKey();
        }
        if (gwPubConfig == null || gwPubConfig.isBlank()) {
            throw new IllegalStateException("Gateway JWT public key is missing. Set GATEWAY_JWT_PUBLIC_KEY or GATEWAY_JWT_PUBLIC_KEY_PATH. Silent fallback is prohibited.");
        }
        this.gatewayPublicKey = loadPublicKey(gwPubConfig);
        log.info("Gateway public key loaded successfully.");

        // 4. Service private key (Required for signing outbound Service JWTs)
        String svcPrivConfig = properties.getServicePrivateKeyPath();
        if (svcPrivConfig == null || svcPrivConfig.isBlank()) {
            svcPrivConfig = properties.getServicePrivateKey();
        }
        if (svcPrivConfig == null || svcPrivConfig.isBlank()) {
            throw new IllegalStateException("Service JWT private key is missing. Set SERVICE_JWT_PRIVATE_KEY or SERVICE_JWT_PRIVATE_KEY_PATH. Silent fallback is prohibited.");
        }
        this.servicePrivateKey = loadPrivateKey(svcPrivConfig);
        log.info("Service private key loaded successfully.");
    }

    private PrivateKey loadPrivateKey(String locationOrPem) {
        try {
            String pem = readContent(locationOrPem);
            String cleanKey = pem
                    .replaceAll("-----[A-Z0-9_ ]+-----", "")
                    .replaceAll("\\s+", "");
            byte[] decoded = Base64.getDecoder().decode(cleanKey);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(new PKCS8EncodedKeySpec(decoded));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse RSA Private Key from: " + locationOrPem, ex);
        }
    }

    private PublicKey loadPublicKey(String locationOrPem) {
        try {
            String pem = readContent(locationOrPem);
            String cleanKey = pem
                    .replaceAll("-----[A-Z0-9_ ]+-----", "")
                    .replaceAll("\\s+", "");
            byte[] decoded = Base64.getDecoder().decode(cleanKey);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(new X509EncodedKeySpec(decoded));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse RSA Public Key from: " + locationOrPem, ex);
        }
    }

    private String readContent(String locationOrContent) throws Exception {
        if (locationOrContent.startsWith("-----BEGIN")) {
            return locationOrContent;
        }
        Resource resource = resourceLoader.getResource(locationOrContent);
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

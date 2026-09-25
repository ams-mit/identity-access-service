package lk.ac.kelaniya.ams.identity_access_service.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for RSA key pair and JWT token signing.
 */
@Configuration
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class RsaKeyProperties {

    /**
     * Path to the RSA private key in PKCS#8 PEM format (e.g. file:certs/private_key.pem or /secrets/private_key.pem).
     */
    private String privateKeyPath;

    /**
     * Path to the RSA public key in X.509 PEM format (e.g. file:certs/public_key.pem or /secrets/public_key.pem).
     */
    private String publicKeyPath;

    /**
     * Path to the Gateway public key in X.509 PEM format for verifying incoming Bearer tokens (e.g. file:certs/gateway_public_key.pem).
     */
    private String gatewayPublicKeyPath;

    /**
     * Optional inline PEM string for Gateway public key.
     */
    private String gatewayPublicKey;

    /**
     * Expiration time in seconds for issued JWTs (default: 1800 / 30 minutes).
     */
    private long expirationSeconds = 1800;

    /**
     * Expiration time in seconds for issued service-to-service JWTs (default: 300 / 5 minutes).
     */
    private long serviceTokenExpirationSeconds = 300;

    /**
     * Key ID (kid) associated with the RSA key pair for JWKS and JWT headers.
     */
    private String keyId = "ams-identity-key-1";
}

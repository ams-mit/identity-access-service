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
     * Path to the RSA private key in PKCS#8 PEM format (e.g. classpath:certs/private_key.pem or file:certs/private_key.pem).
     */
    private String privateKeyPath;

    /**
     * Path to the RSA public key in X.509 PEM format (e.g. classpath:certs/public_key.pem or file:certs/public_key.pem).
     */
    private String publicKeyPath;

    /**
     * Expiration time in seconds for issued JWTs (default: 1800 / 30 minutes).
     */
    private long expirationSeconds = 1800;

    /**
     * Key ID (kid) associated with the RSA key pair for JWKS and JWT headers.
     */
    private String keyId = "ams-identity-key-1";
}

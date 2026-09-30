package kln.ams.identityaccess.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class RsaKeyProperties {

    private String privateKeyPath;
    private String privateKey;

    private String publicKeyPath;
    private String publicKey;

    private String gatewayPublicKeyPath;
    private String gatewayPublicKey;

    private String servicePrivateKeyPath;
    private String servicePrivateKey;

    private long expirationSeconds = 1800;
    private long serviceTokenExpirationSeconds = 300;
}

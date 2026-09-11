package lk.ac.kelaniya.ams.identity_access_service.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Standalone utility to generate 2048-bit RSA key pairs in PKCS#8 / X.509 PEM format.
 * Intended for local development and CI provisioning. NOT executed on normal application startup.
 */
public final class RsaKeyPairGenerator {

    private static final int DEFAULT_KEY_SIZE = 2048;
    private static final String ALGORITHM = "RSA";

    private RsaKeyPairGenerator() {
    }

    public static KeyPair generateKeyPair(int keySize) throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM);
        generator.initialize(Math.max(keySize, DEFAULT_KEY_SIZE));
        return generator.generateKeyPair();
    }

    public static String toPrivateKeyPem(KeyPair keyPair) {
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(keyPair.getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n";
    }

    public static String toPublicKeyPem(KeyPair keyPair) {
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(keyPair.getPublic().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----\n";
    }

    public static void writeKeys(KeyPair keyPair, Path privateKeyPath, Path publicKeyPath) throws IOException {
        if (privateKeyPath.getParent() != null) {
            Files.createDirectories(privateKeyPath.getParent());
        }
        if (publicKeyPath.getParent() != null) {
            Files.createDirectories(publicKeyPath.getParent());
        }

        Files.writeString(privateKeyPath, toPrivateKeyPem(keyPair));
        Files.writeString(publicKeyPath, toPublicKeyPem(keyPair));
    }

    public static void main(String[] args) {
        Path privateKeyPath = args.length > 0 ? Paths.get(args[0]) : Paths.get("certs", "private_key.pem");
        Path publicKeyPath = args.length > 1 ? Paths.get(args[1]) : Paths.get("certs", "public_key.pem");

        try {
            System.out.println("Generating 2048-bit RSA key pair for RS256 JWT signing...");
            KeyPair keyPair = generateKeyPair(DEFAULT_KEY_SIZE);

            writeKeys(keyPair, privateKeyPath, publicKeyPath);

            System.out.println("RSA keys generated successfully:");
            System.out.println("  Private Key: " + privateKeyPath.toAbsolutePath().normalize());
            System.out.println("  Public Key:  " + publicKeyPath.toAbsolutePath().normalize());
        } catch (Exception e) {
            System.err.println("Failed to generate RSA key pair: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

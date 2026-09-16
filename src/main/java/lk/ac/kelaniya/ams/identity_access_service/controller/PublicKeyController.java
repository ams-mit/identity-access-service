package lk.ac.kelaniya.ams.identity_access_service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.JwksResponse;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.PublicKeyResponse;
import lk.ac.kelaniya.ams.identity_access_service.security.RsaKeyProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;

/**
 * Controller exposing the RSA public key for RS256 token verification by the API Gateway and resource servers.
 * ONLY the public key is exposed through these endpoints — the private key is strictly kept internal.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Public Key / JWKS", description = "RSA public key and JWKS discovery endpoints for token verification")
public class PublicKeyController {

    private final RsaKeyProvider rsaKeyProvider;

    /**
     * Exposes the public verification key in X.509 PEM format.
     */
    @GetMapping(value = "/public-key", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get RSA public key",
            description = "Exposes the RSA public key in X.509 PEM format for RS256 token verification by API Gateway and resource servers."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "RSA public key in X.509 PEM format",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PublicKeyResponse.class))
            )
    })
    public ResponseEntity<PublicKeyResponse> getPublicKey() {
        PublicKeyResponse response = PublicKeyResponse.builder()
                .algorithm("RS256")
                .format("X.509")
                .keyId(rsaKeyProvider.getKeyId())
                .publicKey(rsaKeyProvider.getPublicKeyPem())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Exposes the public verification key in standard RFC 7517 JWKS format.
     */
    @GetMapping(value = {"/.well-known/jwks.json", "/jwks.json"}, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get JSON Web Key Set (JWKS)",
            description = "Exposes the RSA public verification key in RFC 7517 compliant JWKS format."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "RFC 7517 compliant JWKS payload",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = JwksResponse.class))
            )
    })
    public ResponseEntity<JwksResponse> getJwks() {
        RSAPublicKey publicKey = rsaKeyProvider.getPublicKey();

        JwksResponse.JwkKey jwk = JwksResponse.JwkKey.builder()
                .keyType("RSA")
                .publicKeyUse("sig")
                .algorithm("RS256")
                .keyId(rsaKeyProvider.getKeyId())
                .modulus(toBase64Url(publicKey.getModulus()))
                .exponent(toBase64Url(publicKey.getPublicExponent()))
                .build();

        JwksResponse response = JwksResponse.builder()
                .keys(List.of(jwk))
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Converts a BigInteger to an unsigned Base64URL string per RFC 7518 Section 6.3.1.
     */
    private static String toBase64Url(BigInteger bigInt) {
        byte[] bytes = bigInt.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] unsigned = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, unsigned, 0, unsigned.length);
            bytes = unsigned;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

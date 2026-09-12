package lk.ac.kelaniya.ams.identity_access_service.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RFC 7517 compliant JSON Web Key Set (JWKS) representation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JwksResponse {

    @JsonProperty("keys")
    private List<JwkKey> keys;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class JwkKey {

        @JsonProperty("kty")
        private String keyType;

        @JsonProperty("use")
        private String publicKeyUse;

        @JsonProperty("alg")
        private String algorithm;

        @JsonProperty("kid")
        private String keyId;

        @JsonProperty("n")
        private String modulus;

        @JsonProperty("e")
        private String exponent;
    }
}

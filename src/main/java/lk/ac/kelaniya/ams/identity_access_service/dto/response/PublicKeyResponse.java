package lk.ac.kelaniya.ams.identity_access_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public key response payload in PEM format.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicKeyResponse {

    private String algorithm;
    private String format;
    private String keyId;
    private String publicKey;
}

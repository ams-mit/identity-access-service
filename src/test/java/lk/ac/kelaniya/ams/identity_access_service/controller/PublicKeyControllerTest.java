package lk.ac.kelaniya.ams.identity_access_service.controller;

import lk.ac.kelaniya.ams.identity_access_service.security.RsaKeyPairGenerator;
import lk.ac.kelaniya.ams.identity_access_service.security.RsaKeyProvider;
import lk.ac.kelaniya.ams.identity_access_service.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicKeyController.class)
@Import(SecurityConfig.class)
class PublicKeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RsaKeyProvider rsaKeyProvider;

    private RSAPublicKey publicKey;
    private String publicKeyPem;
    private final String keyId = "test-key-id-123";

    @BeforeEach
    void setUp() throws Exception {
        KeyPair keyPair = RsaKeyPairGenerator.generateKeyPair(2048);
        this.publicKey = (RSAPublicKey) keyPair.getPublic();
        this.publicKeyPem = RsaKeyPairGenerator.toPublicKeyPem(keyPair);

        given(rsaKeyProvider.getPublicKey()).willReturn(publicKey);
        given(rsaKeyProvider.getPublicKeyPem()).willReturn(publicKeyPem);
        given(rsaKeyProvider.getKeyId()).willReturn(keyId);
    }

    @Test
    @DisplayName("GET /api/v1/auth/public-key returns 200 with X.509 PEM and never exposes private key")
    void testGetPublicKey_returnsPem() throws Exception {
        mockMvc.perform(get("/api/v1/auth/public-key")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.algorithm", is("RS256")))
                .andExpect(jsonPath("$.format", is("X.509")))
                .andExpect(jsonPath("$.keyId", is(keyId)))
                .andExpect(jsonPath("$.publicKey", containsString("-----BEGIN PUBLIC KEY-----")))
                .andExpect(jsonPath("$.publicKey", containsString("-----END PUBLIC KEY-----")))
                .andExpect(content().string(not(containsString("PRIVATE KEY"))));
    }

    @Test
    @DisplayName("GET /api/v1/auth/.well-known/jwks.json returns 200 with RFC 7517 compliant JWKS")
    void testGetJwks_returnsJwks() throws Exception {
        mockMvc.perform(get("/api/v1/auth/.well-known/jwks.json")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.keys", hasSize(1)))
                .andExpect(jsonPath("$.keys[0].kty", is("RSA")))
                .andExpect(jsonPath("$.keys[0].use", is("sig")))
                .andExpect(jsonPath("$.keys[0].alg", is("RS256")))
                .andExpect(jsonPath("$.keys[0].kid", is(keyId)))
                .andExpect(jsonPath("$.keys[0].n").isNotEmpty())
                .andExpect(jsonPath("$.keys[0].e").isNotEmpty())
                .andExpect(content().string(not(containsString("PRIVATE KEY"))));
    }

    @Test
    @DisplayName("GET /api/v1/auth/jwks.json alias returns 200 with RFC 7517 compliant JWKS")
    void testGetJwksAlias_returnsJwks() throws Exception {
        mockMvc.perform(get("/api/v1/auth/jwks.json")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.keys", hasSize(1)))
                .andExpect(jsonPath("$.keys[0].kty", is("RSA")));
    }
}

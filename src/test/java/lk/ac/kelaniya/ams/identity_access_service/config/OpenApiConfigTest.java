package lk.ac.kelaniya.ams.identity_access_service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    @Test
    @DisplayName("OpenApiConfig produces OpenAPI specification with expected metadata and global Bearer JWT security scheme")
    void testOpenApiConfiguration() {
        OpenApiConfig config = new OpenApiConfig();
        OpenAPI openAPI = config.identityAccessOpenAPI();

        assertThat(openAPI).isNotNull();

        // 1. Validate API Info
        assertThat(openAPI.getInfo()).isNotNull();
        assertThat(openAPI.getInfo().getTitle()).isEqualTo("Identity Access Service API");
        assertThat(openAPI.getInfo().getVersion()).isEqualTo("0.1.0");
        assertThat(openAPI.getInfo().getDescription()).contains("Apartment Management System (Group 1)");

        // 2. Validate Security Scheme Components
        assertThat(openAPI.getComponents()).isNotNull();
        assertThat(openAPI.getComponents().getSecuritySchemes()).containsKey(OpenApiConfig.SECURITY_SCHEME_NAME);

        SecurityScheme scheme = openAPI.getComponents().getSecuritySchemes().get(OpenApiConfig.SECURITY_SCHEME_NAME);
        assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(scheme.getScheme()).isEqualTo("bearer");
        assertThat(scheme.getBearerFormat()).isEqualTo("JWT");

        // 3. Validate Global Security Requirement
        assertThat(openAPI.getSecurity()).isNotEmpty();
        assertThat(openAPI.getSecurity().get(0)).containsKey(OpenApiConfig.SECURITY_SCHEME_NAME);
    }
}

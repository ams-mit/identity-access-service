package lk.ac.kelaniya.ams.identity_access_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new JwtAuthenticationFilter(jwtService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Service token authenticates as ServicePrincipal with ROLE_SERVICE and non-UUID subject")
    void testDoFilterInternal_serviceToken_authenticatesServicePrincipal() throws Exception {
        String token = "valid.service.token";
        String serviceName = "resident-management-service";

        @SuppressWarnings("unchecked")
        Jws<Claims> claimsJws = (Jws<Claims>) mock(Jws.class);
        Claims claims = mock(Claims.class);
        given(claimsJws.getPayload()).willReturn(claims);
        given(claims.get("type", String.class)).willReturn("service");
        given(claims.getSubject()).willReturn(serviceName);
        given(jwtService.parseAndValidateToken(token)).willReturn(claimsJws);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isInstanceOf(ServicePrincipal.class);

        ServicePrincipal principal = (ServicePrincipal) auth.getPrincipal();
        assertThat(principal.getServiceName()).isEqualTo(serviceName);
        assertThat(principal.getName()).isEqualTo(serviceName);
        assertThat(principal.getTokenType()).isEqualTo("service");

        List<String> authorityNames = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        assertThat(authorityNames).containsExactlyInAnyOrder("ROLE_SERVICE", "ROLE_INTERNAL_SERVICE");
    }

    @Test
    @DisplayName("User token authenticates as UserPrincipal with mapped roles")
    void testDoFilterInternal_userToken_authenticatesUserPrincipal() throws Exception {
        String token = "valid.user.token";
        UUID userId = UUID.randomUUID();
        String email = "resident@ams.lk";

        @SuppressWarnings("unchecked")
        Jws<Claims> claimsJws = (Jws<Claims>) mock(Jws.class);
        Claims claims = mock(Claims.class);
        given(claimsJws.getPayload()).willReturn(claims);
        given(claims.get("type", String.class)).willReturn("user");
        given(claims.getSubject()).willReturn(userId.toString());
        given(claims.get("roles", List.class)).willReturn(List.of("TENANT_RESIDENT"));
        given(jwtService.parseAndValidateToken(token)).willReturn(claimsJws);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isInstanceOf(UserPrincipal.class);

        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        assertThat(principal.getUserId()).isEqualTo(userId);
        assertThat(principal.getEmail()).isNull();
        assertThat(principal.getRoles()).containsExactly("TENANT_RESIDENT");

        List<String> authorityNames = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        assertThat(authorityNames).contains("ROLE_TENANT_RESIDENT", "TENANT_RESIDENT");
    }

    @Test
    @DisplayName("Token with missing type claim is rejected and clears SecurityContext")
    void testDoFilterInternal_missingTypeClaim_clearsSecurityContext() throws Exception {
        String token = "missing.type.token";

        @SuppressWarnings("unchecked")
        Jws<Claims> claimsJws = (Jws<Claims>) mock(Jws.class);
        Claims claims = mock(Claims.class);
        given(claimsJws.getPayload()).willReturn(claims);
        given(claims.get("type", String.class)).willReturn(null);
        given(jwtService.parseAndValidateToken(token)).willReturn(claimsJws);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Token with explicit null type claim is rejected and clears SecurityContext")
    void testDoFilterInternal_nullTypeClaim_clearsSecurityContext() throws Exception {
        String token = "null.type.token";

        @SuppressWarnings("unchecked")
        Jws<Claims> claimsJws = (Jws<Claims>) mock(Jws.class);
        Claims claims = mock(Claims.class);
        given(claimsJws.getPayload()).willReturn(claims);
        given(claims.get("type", String.class)).willReturn(null);
        given(jwtService.parseAndValidateToken(token)).willReturn(claimsJws);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Token with unexpected type 'admin' is rejected and clears SecurityContext")
    void testDoFilterInternal_unexpectedTypeAdmin_clearsSecurityContext() throws Exception {
        String token = "admin.type.token";

        @SuppressWarnings("unchecked")
        Jws<Claims> claimsJws = (Jws<Claims>) mock(Jws.class);
        Claims claims = mock(Claims.class);
        given(claimsJws.getPayload()).willReturn(claims);
        given(claims.get("type", String.class)).willReturn("admin");
        given(jwtService.parseAndValidateToken(token)).willReturn(claimsJws);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Token with wrong-cased type 'SERVICE' is rejected and clears SecurityContext")
    void testDoFilterInternal_unexpectedCaseService_clearsSecurityContext() throws Exception {
        String token = "uppercase.service.token";

        @SuppressWarnings("unchecked")
        Jws<Claims> claimsJws = (Jws<Claims>) mock(Jws.class);
        Claims claims = mock(Claims.class);
        given(claimsJws.getPayload()).willReturn(claims);
        given(claims.get("type", String.class)).willReturn("SERVICE");
        given(jwtService.parseAndValidateToken(token)).willReturn(claimsJws);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Token with wrong-cased type 'USER' is rejected and clears SecurityContext")
    void testDoFilterInternal_unexpectedCaseUser_clearsSecurityContext() throws Exception {
        String token = "uppercase.user.token";

        @SuppressWarnings("unchecked")
        Jws<Claims> claimsJws = (Jws<Claims>) mock(Jws.class);
        Claims claims = mock(Claims.class);
        given(claimsJws.getPayload()).willReturn(claims);
        given(claims.get("type", String.class)).willReturn("USER");
        given(jwtService.parseAndValidateToken(token)).willReturn(claimsJws);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Invalid or expired token clears SecurityContext")
    void testDoFilterInternal_invalidToken_clearsContext() throws Exception {
        String invalidToken = "bad.token";
        given(jwtService.parseAndValidateToken(invalidToken))
                .willThrow(new JwtException("Expired token"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + invalidToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Request without Bearer header passes through without setting authentication")
    void testDoFilterInternal_noAuthorizationHeader_noAuth() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}

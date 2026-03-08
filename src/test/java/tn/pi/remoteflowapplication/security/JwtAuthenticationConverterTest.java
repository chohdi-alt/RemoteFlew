package tn.pi.remoteflowapplication.security;

import org.junit.jupiter.api.Test;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

import tn.pi.remoteflowapplication.config.SecurityConfig;

public class JwtAuthenticationConverterTest {

    @Test
    void shouldConvertKeycloakRolesToGrantedAuthorities() throws Exception {
        // Arrange
        Converter<Jwt, AbstractAuthenticationToken> converter = extractConverter();

        Map<String, Object> realmAccess = Map.of("roles", List.of("ADMIN", "USER"));
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("realm_access", realmAccess)
                .build();

        // Act
        AbstractAuthenticationToken token = converter.convert(jwt);

        // Assert
        assertThat(token).isNotNull();
        Collection<GrantedAuthority> authorities = token.getAuthorities();
        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void shouldHandleExistingRolePrefix() throws Exception {
        // Arrange
        Converter<Jwt, AbstractAuthenticationToken> converter = extractConverter();

        Map<String, Object> realmAccess = Map.of("roles", List.of("ROLE_MANAGER"));
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("realm_access", realmAccess)
                .build();

        // Act
        AbstractAuthenticationToken token = converter.convert(jwt);

        // Assert
        assertThat(token).isNotNull();
        Collection<GrantedAuthority> authorities = token.getAuthorities();
        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_MANAGER");
    }

    @Test
    void shouldReturnEmptyIfNoRealmAccess() throws Exception {
        // Arrange
        Converter<Jwt, AbstractAuthenticationToken> converter = extractConverter();

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user") // Add required claim
                .build(); // No realm_access

        // Act
        AbstractAuthenticationToken token = converter.convert(jwt);

        // Assert
        assertThat(token).isNotNull();
        assertThat(token.getAuthorities()).isEmpty();
    }

    @Test
    void shouldUsePreferredUsernameAsPrincipalName() throws Exception {
        Converter<Jwt, AbstractAuthenticationToken> converter = extractConverter();

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "8c4af0fa-9f75-4a0c-8a34-86dbc4dbf3fd")
                .claim("preferred_username", "employee-1")
                .claim("realm_access", Map.of("roles", List.of("EMPLOYEE")))
                .build();

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(token).isNotNull();
        assertThat(token.getName()).isEqualTo("employee-1");
    }

    @SuppressWarnings("unchecked")
    private Converter<Jwt, AbstractAuthenticationToken> extractConverter() throws Exception {
        SecurityConfig config = new SecurityConfig();
        Method method = SecurityConfig.class.getDeclaredMethod("jwtAuthenticationConverter");
        method.setAccessible(true);
        return (Converter<Jwt, AbstractAuthenticationToken>) method.invoke(config);
    }
}

package tn.pi.remoteflowapplication.security;

import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import tn.pi.remoteflowapplication.config.SecurityConfig;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtRobustnessTest {

    @Test
    void whenRolesAreMalformed_thenNoExceptionAndNoAuthorities() throws Exception {
        Converter<Jwt, AbstractAuthenticationToken> converter = extractConverter();

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("realm_access", Map.of("roles", "ADMIN,USER"))
                .build();

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(token.getAuthorities()).isEmpty();
    }

    @Test
    void whenRoleContainsPathTraversal_thenSanitized() throws Exception {
        Converter<Jwt, AbstractAuthenticationToken> converter = extractConverter();

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("realm_access",
                        Map.of("roles", List.of("ADMIN", "../../../etc/passwd")))
                .build();

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(token.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @SuppressWarnings("unchecked")
    private Converter<Jwt, AbstractAuthenticationToken> extractConverter() throws Exception {
        SecurityConfig config = new SecurityConfig();
        Method method = SecurityConfig.class.getDeclaredMethod("jwtAuthenticationConverter");
        method.setAccessible(true);
        return (Converter<Jwt, AbstractAuthenticationToken>) method.invoke(config);
    }
}


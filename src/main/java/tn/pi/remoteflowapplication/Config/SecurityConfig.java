package tn.pi.remoteflowapplication.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        BearerTokenAuthenticationEntryPoint authenticationEntryPoint = new BearerTokenAuthenticationEntryPoint();
        AccessDeniedHandlerImpl accessDeniedHandler = new AccessDeniedHandlerImpl();

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            logger.warn("event=UNAUTHORIZED_ACCESS method={} path={} message={}",
                                    request.getMethod(),
                                    request.getRequestURI(),
                                    authException.getMessage());
                            authenticationEntryPoint.commence(request, response, authException);
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            logger.warn("event=ACCESS_DENIED method={} path={} message={}",
                                    request.getMethod(),
                                    request.getRequestURI(),
                                    accessDeniedException.getMessage());
                            accessDeniedHandler.handle(request, response, accessDeniedException);
                        }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/manager/**").hasRole("MANAGER")
                        .requestMatchers("/api/hr/**").hasRole("HR")
                        .requestMatchers("/api/employee/**").hasRole("EMPLOYEE")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(
                        oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        logger.info("event=SECURITY_FILTER_CHAIN_READY mode=STATELESS oauth2ResourceServer=JWT");
        return http.build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtGrantedAuthoritiesConverter converter = new JwtGrantedAuthoritiesConverter();

        JwtAuthenticationConverter jwtAuthConverter = new JwtAuthenticationConverter();
        jwtAuthConverter.setPrincipalClaimName("preferred_username");
        jwtAuthConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Set<GrantedAuthority> authorities = new LinkedHashSet<>();
            authorities.addAll(extractRealmAuthorities(jwt));
            authorities.addAll(extractResourceAuthorities(jwt));

            Collection<GrantedAuthority> defaultAuthorities = converter.convert(jwt);
            if (defaultAuthorities != null && !defaultAuthorities.isEmpty()) {
                authorities.addAll(defaultAuthorities);
            }

            return List.copyOf(authorities);
        });

        return jwtAuthConverter;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
                "http://localhost:4200",
                "http://127.0.0.1:4200"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin",
                "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private Collection<GrantedAuthority> extractRealmAuthorities(Jwt jwt) {
        Object realmAccessRaw = jwt.getClaim("realm_access");

        if (!(realmAccessRaw instanceof Map<?, ?> realmAccess)) {
            return List.of();
        }

        Object rolesObj = realmAccess.get("roles");

        if (!(rolesObj instanceof List<?> roles)) {
            return List.of();
        }

        return roles.stream()
                .filter(role -> role instanceof String)
                .map(role -> (String) role)
                .collect(Collectors.collectingAndThen(Collectors.toList(), this::sanitizeAuthorities));
    }

    private Collection<GrantedAuthority> extractResourceAuthorities(Jwt jwt) {
        Object resourceAccessRaw = jwt.getClaim("resource_access");
        if (!(resourceAccessRaw instanceof Map<?, ?> resourceAccess)) {
            return List.of();
        }

        Set<String> roles = new LinkedHashSet<>();

        for (Object clientAccessRaw : resourceAccess.values()) {
            if (!(clientAccessRaw instanceof Map<?, ?> clientAccess)) {
                continue;
            }

            Object rolesRaw = clientAccess.get("roles");
            if (!(rolesRaw instanceof List<?> clientRoles)) {
                continue;
            }

            for (Object roleRaw : clientRoles) {
                if (roleRaw instanceof String role) {
                    roles.add(role);
                }
            }
        }

        return sanitizeAuthorities(roles);
    }

    private Collection<GrantedAuthority> sanitizeAuthorities(Collection<String> roles) {
        return roles.stream()
                .map(role -> role == null ? "" : role.trim())
                .filter(role -> !role.isBlank())
                .filter(role -> !role.contains("..") && !role.contains("/") && !role.contains("\\"))
                .map(role -> role.startsWith("ROLE_") ? role.toUpperCase(Locale.ROOT) : ("ROLE_" + role).toUpperCase(Locale.ROOT))
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

}

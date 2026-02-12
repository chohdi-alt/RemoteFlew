package tn.pi.remoteflowapplication.security;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
                "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://test-issuer"
})
@AutoConfigureMockMvc
@Import({ JwtSecurityValidationIT.JwtTestConfig.class, TestSecurityController.class })
class JwtSecurityValidationIT {

        @Autowired
        MockMvc mockMvc;

        @Autowired
        JwtEncoder jwtEncoder;

        /* ---------- EXPIRATION ---------- */
        @Test
        void whenExpiredToken_thenUnauthorized() throws Exception {
                JwtClaimsSet claims = JwtClaimsSet.builder()
                                .issuer("http://test-issuer")
                                .subject("user1")
                                .expiresAt(Instant.now().minusSeconds(60))
                                .claim("realm_access", Map.of("roles", List.of("ADMIN")))
                                .build();

                String token = jwtEncoder.encode(JwtEncoderParameters.from(claims))
                                .getTokenValue();

                mockMvc.perform(get("/api/admin/test")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                                .andExpect(status().isUnauthorized());
        }

        /* ---------- WRONG ISSUER ---------- */
        @Test
        void whenWrongIssuer_thenUnauthorized() throws Exception {
                JwtClaimsSet claims = JwtClaimsSet.builder()
                                .issuer("http://evil-issuer")
                                .subject("user1")
                                .expiresAt(Instant.now().plusSeconds(3600))
                                .claim("realm_access", Map.of("roles", List.of("ADMIN")))
                                .build();

                String token = jwtEncoder.encode(JwtEncoderParameters.from(claims))
                                .getTokenValue();

                mockMvc.perform(get("/api/admin/test")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                                .andExpect(status().isUnauthorized());
        }

        /* ---------- INVALID SIGNATURE ---------- */
        @Test
        void whenInvalidSignature_thenUnauthorized() throws Exception {
                // Token signed with another key
                KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
                gen.initialize(2048);
                KeyPair wrongKey = gen.generateKeyPair();

                RSAKey rsaKey = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) wrongKey.getPublic())
                                .privateKey((java.security.interfaces.RSAPrivateKey) wrongKey.getPrivate())
                                .build();

                JwtEncoder wrongEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));

                JwtClaimsSet claims = JwtClaimsSet.builder()
                                .issuer("http://test-issuer")
                                .subject("user1")
                                .expiresAt(Instant.now().plusSeconds(3600))
                                .claim("realm_access", Map.of("roles", List.of("ADMIN")))
                                .build();

                String token = wrongEncoder.encode(JwtEncoderParameters.from(claims))
                                .getTokenValue();

                mockMvc.perform(get("/api/admin/test")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                                .andExpect(status().isUnauthorized());
        }

        /* ---------- JWT TEST CONFIG ---------- */
        static class JwtTestConfig {

                private static final RSAKey rsaKey;

                static {
                        try {
                                KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
                                gen.initialize(2048);
                                KeyPair keyPair = gen.generateKeyPair();
                                rsaKey = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) keyPair.getPublic())
                                                .privateKey((java.security.interfaces.RSAPrivateKey) keyPair
                                                                .getPrivate())
                                                .build();
                        } catch (Exception e) {
                                throw new RuntimeException(e);
                        }
                }

                @Bean
                JwtEncoder jwtEncoder() {
                        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
                }

                @Bean
                JwtDecoder jwtDecoder() throws Exception {
                        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
                        OAuth2TokenValidator<Jwt> defaultValidator = JwtValidators
                                        .createDefaultWithIssuer("http://test-issuer");
                        decoder.setJwtValidator(defaultValidator);
                        return decoder;
                }
        }
}

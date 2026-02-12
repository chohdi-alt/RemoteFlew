package tn.pi.remoteflowapplication.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;

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
class RealJwtIntegrationTest {

        @Autowired
        MockMvc mockMvc;

        @Autowired
        JwtEncoder jwtEncoder;

        @Test
        void withValidSignedJwt_thenAuthenticated() throws Exception {
                JwtClaimsSet claims = JwtClaimsSet.builder()
                                .issuer("http://test-issuer")
                                .subject("admin01")
                                .expiresAt(Instant.now().plusSeconds(3600))
                                .claim("realm_access", Map.of("roles", List.of("ADMIN")))
                                .build();

                String token = jwtEncoder.encode(JwtEncoderParameters.from(claims))
                                .getTokenValue();

                mockMvc.perform(get("/api/admin/test")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                                .andExpect(status().isOk());
        }
}

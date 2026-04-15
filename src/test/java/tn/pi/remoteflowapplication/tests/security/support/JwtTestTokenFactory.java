package tn.pi.remoteflowapplication.tests.security.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtTestTokenFactory {

    static final byte[] TEST_SIGNING_KEY = "remoteflow-test-signing-key-256-bit!".getBytes(StandardCharsets.UTF_8);
    private static final String ISSUER = "https://security-tests.remoteflow.local";
    private static final String AUDIENCE = "remoteflow-api";

    public String bearerTokenForRole(String username, String role) {
        return bearerToken(username, List.of(role));
    }

    public String bearerToken(String username, List<String> roles) {
        Instant now = Instant.now();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .subject("sub-" + username)
                .claim("preferred_username", username)
                .claim("realm_access", Map.of("roles", roles))
                .issueTime(java.util.Date.from(now.minusSeconds(5)))
                .expirationTime(java.util.Date.from(now.plusSeconds(3600)))
                .build();

        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256)
                        .type(JOSEObjectType.JWT)
                        .build(),
                claims);

        try {
            jwt.sign(new MACSigner(TEST_SIGNING_KEY));
            return "Bearer " + jwt.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException("Unable to generate test JWT", ex);
        }
    }
}

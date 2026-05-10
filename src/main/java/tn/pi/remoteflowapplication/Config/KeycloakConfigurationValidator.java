package tn.pi.remoteflowapplication.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
public class KeycloakConfigurationValidator implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(KeycloakConfigurationValidator.class);

    @Value("${keycloak.client-id:}")
    private String clientId;

    @Value("${keycloak.client-secret:}")
    private String clientSecret;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
    private String issuerUri;

    @Override
    public void run(ApplicationArguments args) {
        validateRequired("keycloak.client-id", clientId);
        validateRequired("keycloak.client-secret", clientSecret);
        validateIssuerReachability();
    }

    private void validateRequired(String key, String value) {
        if (value == null || value.isBlank()) {
            logger.error("event=SECURITY_CONFIG_INVALID property={} message=Property is missing or blank", key);
            return;
        }
        logger.info("event=SECURITY_CONFIG_OK property={}", key);
    }

    private void validateIssuerReachability() {
        if (issuerUri == null || issuerUri.isBlank()) {
            logger.error(
                    "event=SECURITY_CONFIG_INVALID property=spring.security.oauth2.resourceserver.jwt.issuer-uri message=Property is missing or blank");
            return;
        }

        String discoveryEndpoint = issuerUri.endsWith("/")
                ? issuerUri + ".well-known/openid-configuration"
                : issuerUri + "/.well-known/openid-configuration";

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(discoveryEndpoint))
                .GET()
                .timeout(Duration.ofSeconds(8))
                .build();

        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();

            if (status >= 200 && status < 400) {
                logger.info(
                        "event=SECURITY_CONFIG_OK property=spring.security.oauth2.resourceserver.jwt.issuer-uri endpoint={} status={}",
                        discoveryEndpoint,
                        status);
                return;
            }

            logger.error(
                    "event=SECURITY_CONFIG_INVALID property=spring.security.oauth2.resourceserver.jwt.issuer-uri endpoint={} status={} message=Issuer endpoint is not reachable",
                    discoveryEndpoint,
                    status);
        } catch (Exception ex) {
            logger.error(
                    "event=SECURITY_CONFIG_INVALID property=spring.security.oauth2.resourceserver.jwt.issuer-uri endpoint={} message={}",
                    discoveryEndpoint,
                    ex.getMessage());
        }
    }
}

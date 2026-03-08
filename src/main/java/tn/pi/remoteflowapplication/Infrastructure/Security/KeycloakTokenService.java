package tn.pi.remoteflowapplication.infrastructure.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import tn.pi.remoteflowapplication.application.dto.AuthTokenResponse;
import tn.pi.remoteflowapplication.domain.exception.AuthenticationFailedException;

import java.time.Duration;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class KeycloakTokenService {

    private static final Logger logger = LoggerFactory.getLogger(KeycloakTokenService.class);

    private static final Duration KEYCLOAK_TIMEOUT = Duration.ofSeconds(15);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${keycloak.auth-server-url}")
    private String authServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    public KeycloakTokenService(WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    public AuthTokenResponse login(String username, String password) {
        LinkedMultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "password");
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("username", username);
        formData.add("password", password);
        return requestToken(formData);
    }

    public AuthTokenResponse refresh(String refreshToken) {
        LinkedMultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "refresh_token");
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("refresh_token", refreshToken);
        return requestToken(formData);
    }

    private AuthTokenResponse requestToken(MultiValueMap<String, String> formData) {
        String grantType = formData.getFirst("grant_type");
        String username = formData.getFirst("username");
        String requestId = UUID.randomUUID().toString();

        try {
            String tokenEndpoint = buildTokenEndpoint();
            logger.info("keycloak.token.request id={} grant_type={} username={}", requestId, grantType, username);
            logger.debug("keycloak.token.endpoint id={} endpoint={}", requestId, tokenEndpoint);

            TokenEndpointResponse response = webClient.post()
                    .uri(tokenEndpoint)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(formData))
                    .exchangeToMono(clientResponse -> clientResponse.bodyToMono(String.class)
                            .defaultIfEmpty("No response body")
                            .map(body -> new TokenEndpointResponse(clientResponse.statusCode(), body)))
                    .block(KEYCLOAK_TIMEOUT);

            if (response == null) {
                throw new RuntimeException("Keycloak token endpoint returned no response");
            }

            int status = response.status.value();
            String responseBody = response.body == null ? "" : response.body;
            logger.info("keycloak.token.response id={} grant_type={} username={} status={}", requestId, grantType,
                    username, status);

            if (response.status.is2xxSuccessful()) {
                String redactedBody = redactTokenResponseBody(responseBody);
                logger.info("keycloak.token.success id={} grant_type={} username={} status={} body={}", requestId,
                        grantType, username, status, redactedBody);
                KeycloakTokenPayload payload = objectMapper.readValue(responseBody, KeycloakTokenPayload.class);

                if (payload.accessToken == null || payload.accessToken.isBlank()) {
                    throw new AuthenticationFailedException("Keycloak returned an empty access token");
                }

                return new AuthTokenResponse(
                        payload.accessToken,
                        payload.refreshToken,
                        payload.tokenType,
                        payload.expiresIn,
                        payload.refreshExpiresIn,
                        extractRoles(payload.accessToken));
            }

            if (response.status.is4xxClientError()) {
                KeycloakError keycloakError = parseKeycloakError(responseBody);
                FailureAnalysis analysis = analyzeKeycloakFailure(keycloakError, responseBody);

                logger.warn(
                        "event=AUTH_LOGIN_FAILURE keycloak.token.rejected requestId={} clientId={} username={} grantType={} status={} error={} description={} classification={} recommendedAction={}",
                        requestId,
                        clientId,
                        username,
                        grantType,
                        status,
                        keycloakError.error,
                        keycloakError.errorDescription,
                        analysis.classification,
                        analysis.recommendedAction);
                logger.debug("keycloak.token.rejected.rawBody requestId={} body={}", requestId, responseBody);

                throw new AuthenticationFailedException(
                        "Invalid credentials or Keycloak rejected password grant",
                        "AUTHENTICATION_FAILED",
                        keycloakError.error,
                        keycloakError.errorDescription,
                        status);
            }

            if (response.status.is5xxServerError()) {
                logger.error("keycloak.token.error id={} grant_type={} username={} status={} raw_body={}",
                        requestId,
                        grantType,
                        username,
                        status,
                        responseBody);
                throw new RuntimeException("Keycloak server error " + status + ": " + responseBody);
            }

            throw new RuntimeException("Unexpected Keycloak response status " + status + ": " + responseBody);
        } catch (AuthenticationFailedException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("Keycloak authentication failed", ex);
        }
    }

    /**
     * Parses the Keycloak token endpoint error response body (typically JSON).
     */
    private KeycloakError parseKeycloakError(String errorBody) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(errorBody, new TypeReference<>() {
            });
            return new KeycloakError(asString(parsed.get("error")), asString(parsed.get("error_description")));
        } catch (Exception ignored) {
            return new KeycloakError(null, null);
        }
    }

    private FailureAnalysis analyzeKeycloakFailure(KeycloakError error, String rawBody) {
        String message = (error.errorDescription != null && !error.errorDescription.isBlank())
                ? error.errorDescription
                : rawBody;
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);

        if (normalized.contains("password is temporary")
                || normalized.contains("temporary password")
                || normalized.contains("update password")) {
            return new FailureAnalysis("b) Temporary password",
                    "In Keycloak user Credentials: set a new password and set Temporary=OFF, then retry.");
        }

        if (normalized.contains("account is not fully set up")
                || normalized.contains("requires action")
                || normalized.contains("required action")
                || normalized.contains("configure otp")
                || normalized.contains("verify email")) {
            return new FailureAnalysis("c) Required action pending",
                    "In Keycloak user settings: clear Required Actions, ensure password is not temporary, and satisfy any enforced actions (e.g. email verification/TOTP).");
        }

        if (normalized.contains("account temporarily disabled")
                || normalized.contains("temporarily disabled")
                || normalized.contains("brute force")
                || normalized.contains("locked")) {
            return new FailureAnalysis("e) Brute force lock",
                    "In Keycloak Realm Settings -> Security Defenses -> Brute Force Detection: clear the user's lock and adjust thresholds if needed.");
        }

        if (normalized.contains("user is disabled")
                || normalized.contains("account disabled")) {
            return new FailureAnalysis("d) Disabled user",
                    "In Keycloak user settings: set Enabled=true, then retry.");
        }

        if (normalized.contains("invalid user credentials")
                || normalized.contains("invalid username or password")
                || normalized.contains("invalid username")
                || normalized.contains("invalid password")) {
            return new FailureAnalysis("a) Wrong password",
                    "Reset the user's password (temporary OFF) and verify the username spelling/case exactly matches the Keycloak username.");
        }

        return new FailureAnalysis("unknown",
                "Check Keycloak user status (Enabled/Required Actions/temporary password/brute force lock) and confirm the username casing matches exactly.");
    }

    private String buildFriendlyAuthFailureMessage(KeycloakError error, String rawBody, FailureAnalysis analysis) {
        String errorCode = error.error == null ? null : error.error.trim();
        String description = error.errorDescription == null ? null : error.errorDescription.trim();

        String baseMessage;
        if (description != null && !description.isBlank() && errorCode != null && !errorCode.isBlank()) {
            baseMessage = errorCode + ": " + description;
        } else if (description != null && !description.isBlank()) {
            baseMessage = description;
        } else if (errorCode != null && !errorCode.isBlank()) {
            baseMessage = errorCode;
        } else {
            baseMessage = rawBody;
        }

        if (analysis == null || analysis.classification == null || analysis.classification.isBlank()) {
            return baseMessage;
        }

        return baseMessage + " [" + analysis.classification + "]";
    }

    private String redactTokenResponseBody(String responseBody) {
        if (responseBody == null) {
            return null;
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(responseBody, new TypeReference<>() {
            });
            if (parsed.containsKey("access_token")) {
                parsed.put("access_token", "<redacted>");
            }
            if (parsed.containsKey("refresh_token")) {
                parsed.put("refresh_token", "<redacted>");
            }
            if (parsed.containsKey("id_token")) {
                parsed.put("id_token", "<redacted>");
            }
            return objectMapper.writeValueAsString(parsed);
        } catch (Exception ignored) {
            // Fall back to a simple string redaction to avoid leaking tokens in logs.
            return responseBody
                    .replaceAll("(?i)(\\\"access_token\\\"\\s*:\\s*\\\")[^\\\"]*(\\\")", "$1<redacted>$2")
                    .replaceAll("(?i)(\\\"refresh_token\\\"\\s*:\\s*\\\")[^\\\"]*(\\\")", "$1<redacted>$2")
                    .replaceAll("(?i)(\\\"id_token\\\"\\s*:\\s*\\\")[^\\\"]*(\\\")", "$1<redacted>$2");
        }
    }

    private static String asString(Object value) {
        if (value instanceof String s) {
            return s;
        }
        return value == null ? null : String.valueOf(value);
    }

    private String buildTokenEndpoint() {
        String normalizedBaseUrl = authServerUrl.endsWith("/")
                ? authServerUrl.substring(0, authServerUrl.length() - 1)
                : authServerUrl;
        return normalizedBaseUrl + "/realms/" + realm + "/protocol/openid-connect/token";
    }

    private List<String> extractRoles(String accessToken) {
        try {
            String[] parts = accessToken.split("\\.");
            if (parts.length < 2) {
                return List.of();
            }

            byte[] decodedPayload = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> claims = objectMapper.readValue(decodedPayload, new TypeReference<>() {
            });

            Object realmAccessRaw = claims.get("realm_access");
            if (!(realmAccessRaw instanceof Map<?, ?> realmAccess)) {
                return List.of();
            }

            Object rolesRaw = realmAccess.get("roles");
            if (!(rolesRaw instanceof Collection<?> roles)) {
                return List.of();
            }

            return roles.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(String::trim)
                    .filter(role -> !role.isBlank())
                    .map(role -> role.startsWith("ROLE_")
                            ? role.toUpperCase(Locale.ROOT)
                            : ("ROLE_" + role).toUpperCase(Locale.ROOT))
                    .distinct()
                    .toList();
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static class KeycloakTokenPayload {
        @JsonProperty("access_token")
        private String accessToken;

        @JsonProperty("refresh_token")
        private String refreshToken;

        @JsonProperty("token_type")
        private String tokenType;

        @JsonProperty("expires_in")
        private Long expiresIn;

        @JsonProperty("refresh_expires_in")
        private Long refreshExpiresIn;
    }

    private static class TokenEndpointResponse {
        private final HttpStatusCode status;
        private final String body;

        private TokenEndpointResponse(HttpStatusCode status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    private static class KeycloakError {
        private final String error;
        private final String errorDescription;

        private KeycloakError(String error, String errorDescription) {
            this.error = error;
            this.errorDescription = errorDescription;
        }
    }

    private static class FailureAnalysis {
        private final String classification;
        private final String recommendedAction;

        private FailureAnalysis(String classification, String recommendedAction) {
            this.classification = classification;
            this.recommendedAction = recommendedAction;
        }
    }
}

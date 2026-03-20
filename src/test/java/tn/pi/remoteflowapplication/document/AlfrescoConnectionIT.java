package tn.pi.remoteflowapplication.document;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import org.springframework.core.ParameterizedTypeReference;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ALFRESCO_IT", matches = "true")

class AlfrescoConnectionIT {

        @Autowired
        private RestTemplate alfrescoRestTemplate;

        @Value("${alfresco.base-url}")
        private String baseUrl;

        @Value("${alfresco.username}")
        private String username;

        @Value("${alfresco.password}")
        private String password;

        @Test
        void should_authenticate_to_alfresco_and_return_ticket() {
                // GIVEN
                String url = baseUrl
                                + "/api/-default-/public/authentication/versions/1/tickets";

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                Map<String, String> body = Map.of(
                                "userId", username,
                                "password", password);

                HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

                // WHEN
                ParameterizedTypeReference<Map<String, Object>> typeRef = new ParameterizedTypeReference<>() {
                };
                ResponseEntity<Map<String, Object>> response = alfrescoRestTemplate.exchange(url, HttpMethod.POST,
                                request, typeRef);

                // THEN
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody()).containsKey("entry");

                Map<String, Object> bodyResponse = response.getBody();
                if (bodyResponse.get("entry") instanceof Map<?, ?> entryRaw) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> entry = (Map<String, Object>) entryRaw;
                        assertThat(entry).containsKey("id");
                        String ticket = (String) entry.get("id");
                        assertThat(ticket).startsWith("TICKET_");
                }
        }
}

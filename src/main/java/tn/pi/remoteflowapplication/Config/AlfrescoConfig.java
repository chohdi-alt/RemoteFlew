package tn.pi.remoteflowapplication.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@ConditionalOnProperty(
    name = "alfresco.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class AlfrescoConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}

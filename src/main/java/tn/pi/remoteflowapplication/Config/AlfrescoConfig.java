package tn.pi.remoteflowapplication.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AlfrescoConfig {
    @Bean
    public RestTemplate alfrescoRestTemplate() {
        return new RestTemplate();
    }
}

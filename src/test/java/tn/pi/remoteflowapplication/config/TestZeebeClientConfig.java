package tn.pi.remoteflowapplication.config;

import io.camunda.zeebe.client.ZeebeClient;
import org.mockito.Answers;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TestZeebeClientConfig {

    @Bean
    @ConditionalOnMissingBean(ZeebeClient.class)
    public ZeebeClient zeebeClient() {
        return Mockito.mock(ZeebeClient.class, Answers.RETURNS_DEEP_STUBS);
    }
}

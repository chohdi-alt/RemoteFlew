package tn.pi.remoteflowapplication.config;

import io.camunda.zeebe.spring.client.annotation.Deployment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "camunda.enabled", havingValue = "true", matchIfMissing = true)
@Deployment(resources = "classpath:telework_process.bpmn")
public class CamundaDeploymentConfig {
}

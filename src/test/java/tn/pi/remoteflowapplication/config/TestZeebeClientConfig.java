package tn.pi.remoteflowapplication.config;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.net.URI;

@TestConfiguration
public class TestZeebeClientConfig {

    @Bean(destroyMethod = "close")
    ZeebeClient zeebeClientForTests(
            @Value("${tests.workflow.grpc-address:http://localhost:26500}") String grpcAddress,
            @Value("${tests.workflow.plaintext:true}") boolean plaintext) {

        ZeebeClientBuilder builder = ZeebeClient.newClientBuilder()
                .grpcAddress(URI.create(grpcAddress));

        if (plaintext) {
            builder.usePlaintext();
        }

        return builder.build();
    }
}

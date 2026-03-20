package tn.pi.remoteflowapplication;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.camunda.zeebe.spring.client.annotation.Deployment;

@Deployment(resources = "classpath:telework_process.bpmn")
@SpringBootApplication
public class RemoteFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(RemoteFlowApplication.class, args);
    }

}

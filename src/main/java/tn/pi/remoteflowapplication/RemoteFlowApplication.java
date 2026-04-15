package tn.pi.remoteflowapplication;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.camunda.zeebe.spring.client.annotation.Deployment;
import io.github.cdimascio.dotenv.Dotenv;

@Deployment(resources = "classpath:telework_process.bpmn")
@SpringBootApplication
@EnableScheduling
public class RemoteFlowApplication {

    public static void main(String[] args) {
        // Load .env into System properties before Spring starts
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));

        SpringApplication.run(RemoteFlowApplication.class, args);
    }

}

package tn.pi.remoteflowapplication.tests.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseIntegrationIT {

    @Container
    protected static final MariaDBContainer<?> mariaDB = new MariaDBContainer<>("mariadb:11")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mariaDB::getJdbcUrl);
        registry.add("spring.datasource.username", mariaDB::getUsername);
        registry.add("spring.datasource.password", mariaDB::getPassword);

        // Explicitly set Flyway to use the same datasource
        registry.add("spring.flyway.url", mariaDB::getJdbcUrl);
        registry.add("spring.flyway.user", mariaDB::getUsername);
        registry.add("spring.flyway.password", mariaDB::getPassword);
        
        // Mocking other infrastructure to avoid localhost connectivity issues in CI
        registry.add("alfresco.base-url", () -> "http://localhost:8080/alfresco");
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> "http://localhost:8081/realms/pfe-realm");
        registry.add("camunda.client.grpc-address", () -> "http://localhost:26500");
    }
}

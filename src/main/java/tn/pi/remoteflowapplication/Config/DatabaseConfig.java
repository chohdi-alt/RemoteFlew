package tn.pi.remoteflowapplication.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
public class DatabaseConfig {
    // Intentionally empty.
    // Spring Boot auto-configures DataSource and JPA from application.properties.
}

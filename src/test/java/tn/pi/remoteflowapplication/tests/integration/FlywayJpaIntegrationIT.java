package tn.pi.remoteflowapplication.tests.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tn.pi.remoteflowapplication.domain.entity.TaskEntity;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.infrastructure.persistence.SpringTaskJpaRepository;
import tn.pi.remoteflowapplication.infrastructure.persistence.SpringTeleworkJpaRepository;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FlywayJpaIntegrationIT {

    @Container
    static final MariaDBContainer<?> MARIA_DB = new MariaDBContainer<>("mariadb:11.4")
            .withDatabaseName("remoteflow_test")
            .withUsername("remoteflow")
            .withPassword("remoteflow");

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MARIA_DB::getJdbcUrl);
        registry.add("spring.datasource.username", MARIA_DB::getUsername);
        registry.add("spring.datasource.password", MARIA_DB::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
        registry.add("spring.flyway.url", MARIA_DB::getJdbcUrl);
        registry.add("spring.flyway.user", MARIA_DB::getUsername);
        registry.add("spring.flyway.password", MARIA_DB::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SpringTeleworkJpaRepository teleworkRepository;

    @Autowired
    private SpringTaskJpaRepository taskRepository;

    @Test
    void shouldApplyAllFlywayMigrationsSuccessfully() {
        // Verify no migrations failed
        Integer failedCount = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = 0",
                Integer.class);
        assertEquals(0, failedCount, "All Flyway migrations should succeed");

        // Verify at least one migration was applied
        Integer successCount = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = 1",
                Integer.class);
        assertTrue(successCount > 0, "At least one Flyway migration should be applied");

        // Verify the latest version is resolvable
        String latestVersion = jdbcTemplate.queryForObject(
                "select version from flyway_schema_history where success = 1 order by installed_rank desc limit 1",
                String.class);
        assertNotNull(latestVersion, "Latest Flyway migration version should not be null");
    }

    @Test
    void shouldPersistAndQueryWorkflowTaskAgainstMigratedSchema() {
        TeleworkRequest request = TeleworkRequest.create(
                "employee.integration",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 2));
        request = teleworkRepository.saveAndFlush(request);

        TaskEntity savedTask = taskRepository.saveAndFlush(new TaskEntity(
                request.getId(),
                90001L,
                "MANAGER",
                "PENDING",
                Instant.now()));

        assertTrue(taskRepository.findByJobKey(savedTask.getJobKey()).isPresent());
        assertEquals(1L, taskRepository.countByTypeAndStatus("MANAGER", "PENDING"));
    }

    @Test
    void shouldEnforceUniqueZeebeJobKeyConstraint() {
        TeleworkRequest request = TeleworkRequest.create(
                "employee.unique",
                LocalDate.of(2026, 4, 3),
                LocalDate.of(2026, 4, 4));
        request = teleworkRepository.saveAndFlush(request);

        taskRepository.saveAndFlush(new TaskEntity(
                request.getId(),
                123456L,
                "MANAGER",
                "PENDING",
                Instant.now()));

        TeleworkRequest secondRequest = TeleworkRequest.create(
                "employee.unique.2",
                LocalDate.of(2026, 4, 5),
                LocalDate.of(2026, 4, 6));
        secondRequest = teleworkRepository.saveAndFlush(secondRequest);

        TaskEntity duplicate = new TaskEntity(
                secondRequest.getId(),
                123456L,
                "HR",
                "PENDING",
                Instant.now());

        assertThrows(DataIntegrityViolationException.class, () -> taskRepository.saveAndFlush(duplicate));
    }
}

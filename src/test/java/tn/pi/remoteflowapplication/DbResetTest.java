package tn.pi.remoteflowapplication;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Disabled("Manual database maintenance utility, not part of automated test suite.")
public class DbResetTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void resetDb() {
        try {
            jdbcTemplate.execute("DROP TABLE IF EXISTS workflow_tasks");
            System.out.println("workflow_tasks dropped successfully.");
            jdbcTemplate.execute("DELETE FROM flyway_schema_history WHERE version = '17'");
            System.out.println("V17 migration removed from flyway_schema_history.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

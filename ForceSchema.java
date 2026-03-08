import java.sql.*;

public class ForceSchema {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:mariadb://localhost:3307/pfeworkflow";
        String user = "root";
        String password = "mariadb";
        try (Connection conn = DriverManager.getConnection(url, user, password);
                Statement stmt = conn.createStatement()) {
            System.out.println("Updating state column to ENUM...");
            stmt.execute(
                    "ALTER TABLE telework_requests MODIFY COLUMN state ENUM('SUBMITTED', 'APPROVED', 'REJECTED', 'SPECIAL') NOT NULL DEFAULT 'SUBMITTED'");
            System.out.println("Done.");
        }
    }
}

package tn.pi.remoteflowapplication;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class DbReset {
    public static void main(String[] args) {
        try {
            Class.forName("org.mariadb.jdbc.Driver");
            Connection conn = DriverManager.getConnection("jdbc:mariadb://localhost:3307/pfeworkflow", "root", "mariadb");
            Statement stmt = conn.createStatement();
            
            stmt.execute("SET FOREIGN_KEY_CHECKS = 0");
            
            // Get all tables
            ResultSet rs = stmt.executeQuery("SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'");
            while (rs.next()) {
                String tableName = rs.getString(1);
                System.out.println("Dropping table: " + tableName);
                stmt.execute("DROP TABLE IF EXISTS `" + tableName + "`");
            }
            
            stmt.execute("SET FOREIGN_KEY_CHECKS = 1");
            System.out.println("All tables dropped. Database is clean for fresh Flyway migration.");
            
            stmt.close();
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

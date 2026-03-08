import java.sql.*;

public class CheckDB {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:mariadb://localhost:3307/pfeworkflow";
        String user = "root";
        String password = "mariadb";
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            DatabaseMetaData metaData = conn.getMetaData();
            System.out.println("--- Table: telework_requests ---");
            try (ResultSet rs = metaData.getColumns(null, null, "telework_requests", "state")) {
                if (rs.next()) {
                    System.out.println("Column: state");
                    System.out.println("Type Name: " + rs.getString("TYPE_NAME"));
                    System.out.println("Column Size: " + rs.getInt("COLUMN_SIZE"));
                } else {
                    System.out.println("Column state NOT FOUND");
                }
            }
            try (ResultSet rs = metaData.getColumns(null, null, "telework_requests", "status")) {
                if (rs.next()) {
                    System.out.println("Column: status FOUND");
                } else {
                    System.out.println("Column status NOT FOUND");
                }
            }
        }
    }
}

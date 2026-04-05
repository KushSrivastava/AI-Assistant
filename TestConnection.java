//import java.sql.Connection;
//import java.sql.DriverManager;
//import java.sql.ResultSet;
//
//public class TestConnection {
//    public static void main(String[] args) {
//        String url = "jdbc:postgresql://127.0.0.1:5433/knowledgebot";
//        String user = "kbot";
//        String password = "password";
//
//        try (Connection conn = DriverManager.getConnection(url, user, password)) {
//            System.out.println("Connected to the PostgreSQL server successfully.");
//            ResultSet rs = conn.createStatement().executeQuery("SELECT 1;");
//            if (rs.next()) {
//                System.out.println("Query result: " + rs.getInt(1));
//            }
//        } catch (Exception e) {
//            System.err.println(e.getMessage());
//            e.printStackTrace();
//        }
//    }
//}

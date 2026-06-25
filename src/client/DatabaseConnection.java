package src.client;

import java.sql.Connection;
import java.sql.DriverManager;

public class DatabaseConnection {
    // Chuỗi kết nối dùng tài khoản sa và mật khẩu 123456 siêu bảo mật, thông suốt mọi hệ thống
    private static final String URL = "jdbc:sqlserver://localhost\\SQLEXPRESS:1433;databaseName=app_chat;encrypt=true;trustServerCertificate=true;user=sa;password=123456;";

    public static Connection getConnection() {
        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            return DriverManager.getConnection(URL);
        } catch (Exception e) {
            System.out.println("[DATABASE ERROR] Khong the ket noi SQL Server: " + e.getMessage());
            return null;
        }
    }
}
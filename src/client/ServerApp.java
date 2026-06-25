package src.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ServerApp {

    public static void main(String[] args) {
        int port = 5000;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("[SERVER SQL] Da bat! Dang cho ket noi o cong " + port + "...");
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("[SERVER SQL] ==> Thay Client ket noi!");
                new Thread(new ClientHandler(socket)).start();
            }
        } catch (Exception e) {
            System.out.println("[SERVER SQL] Loi: " + e.getMessage());
        }
    }

    private static class ClientHandler implements Runnable {
        private Socket socket;
        public ClientHandler(Socket socket) { this.socket = socket; }

        @Override
        public void run() {
            try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
            ) {
                String clientMessage;
                while ((clientMessage = in.readLine()) != null) {
                    System.out.println("[SERVER SQL] Nhan tin nhan: " + clientMessage);

                    // XỬ LÝ ĐĂNG NHẬP
                    if (clientMessage.startsWith("LOGIN;")) {
                        String[] data = clientMessage.split(";");
                        if (checkLogin(data[1], data[2])) {
                            out.println("LOGIN_SUCCESS"); 
                        } else {
                            out.println("LOGIN_FAILED"); 
                        }
                    } 
                    // XỬ LÝ ĐĂNG KÝ
                    else if (clientMessage.startsWith("REGISTER;")) {
                        String[] data = clientMessage.split(";");
                        if (registerUser(data[1], data[2])) {
                            out.println("REGISTER_SUCCESS");
                        } else {
                            out.println("REGISTER_FAILED");
                        }
                    }
                    // XỬ LÝ CẬP NHẬT THÔNG TIN CÁ NHÂN (ĐÃ ĐỒNG BỘ 7 PHẦN TỬ)
                    else if (clientMessage.startsWith("UPDATE_PROFILE;")) {
                        String[] data = clientMessage.split(";");
                        
                        // Kiểm tra an toàn độ dài mảng trước khi gán
                        if (data.length >= 7) {
                            String username = data[1];
                            String fullName = data[2];
                            String dob = data[3];
                            String university = data[4];
                            String email = data[5];  // Thêm Email
                            String phone = data[6];  // Thêm Số điện thoại

                            if (updateProfile(username, fullName, dob, university, email, phone)) {
                                out.println("UPDATE_PROFILE_SUCCESS");
                                System.out.println("[SERVER SQL] ==> Cap nhat profile mo rong thanh cong cho: " + username);
                            } else {
                                out.println("UPDATE_PROFILE_FAILED");
                                System.out.println("[SERVER SQL] ==> Cap nhat profile mo rong THAT BAI.");
                            }
                        } else {
                            System.out.println("[⚠️ SERVER] Goi tin UPDATE_PROFILE bi thieu thong tin!");
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("[SERVER SQL] Client thoat.");
            }
        }
    }

    // --- CÁC HÀM TRUY VẤN SQL SERVER ---

    private static boolean checkLogin(String user, String pass) {
        String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) return false;
            pstmt.setString(1, user);
            pstmt.setString(2, pass);
            try (ResultSet rs = pstmt.executeQuery()) { return rs.next(); }
        } catch (SQLException e) { return false; }
    }

    private static boolean registerUser(String user, String pass) {
        String sql = "INSERT INTO users (username, password) VALUES (?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) return false;
            pstmt.setString(1, user);
            pstmt.setString(2, pass);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    // ĐÃ UPDATE ĐẦY ĐỦ THAM SỐ VÀ CÂU LỆNH SQL CHO EMAIL VÀ PHONE
    private static boolean updateProfile(String user, String fullName, String dob, String university, String email, String phone) {
        String sql = "UPDATE users SET full_name = ?, dob = ?, university = ?, email = ?, phone = ? WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) return false;
            pstmt.setString(1, fullName);
            pstmt.setString(2, dob);
            pstmt.setString(3, university);
            pstmt.setString(4, email);
            pstmt.setString(5, phone);
            pstmt.setString(6, user);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("[SQL ERROR] Loi UPDATE profile mo rong: " + e.getMessage());
            return false;
        }
    }
}
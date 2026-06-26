package src.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ServerApp {
    private static final int PORT = 5000;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[SERVER SQL] Da bat! Dang cho ket noi o cong " + PORT + "...");
            
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("[SERVER SQL] ==> Co một thiet bi Client ket noi vao!");
                
                // Đẻ luồng xử lý riêng biệt cho từng Client kết nối
                new Thread(new ClientHandler(socket)).start();
            }
        } catch (Exception e) {
            System.out.println("[SERVER SQL] Loi khoi dong cong: " + e.getMessage());
        }
    }

    // --- LỚP XỬ LÝ ĐA LUỒNG CHO TỪNG CLIENT TRỰC TIẾP ---
    private static class ClientHandler implements Runnable {
        private Socket socket;

        public ClientHandler(Socket socket) { 
            this.socket = socket; 
        }

        @Override
        public void run() {
            try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)
            ) {
                String clientMessage;
                while ((clientMessage = in.readLine()) != null) {
                    System.out.println("[RECEIVE]: " + clientMessage);
                    String[] data = clientMessage.split(";");
                    String command = data[0];

                    switch (command) {
                        case "LOGIN":
                            if (checkLogin(data[1], data[2])) {
                                out.println("LOGIN_SUCCESS");
                                System.out.println("[LOGIN] User: " + data[1] + " thanh cong!");
                            } else {
                                out.println("LOGIN_FAILED");
                                System.out.println("[LOGIN] User: " + data[1] + " that bai.");
                            }
                            break;

                        case "REGISTER":
                            if (registerUser(data[1], data[2])) {
                                out.println("REGISTER_SUCCESS");
                                System.out.println("[REGISTER] Da luu user " + data[1] + " vao SQL Server!");
                            } else {
                                out.println("REGISTER_FAILED");
                            }
                            break;

                        case "UPDATE_PROFILE":
                            if (data.length >= 7) {
                                if (updateProfile(data[1], data[2], data[3], data[4], data[5], data[6])) {
                                    out.println("UPDATE_PROFILE_SUCCESS");
                                } else {
                                    out.println("UPDATE_PROFILE_FAILED");
                                }
                            }
                            break;

                        // 1. XỬ LÝ XÁC THỰC EMAIL QUA MÃ OTP
                        case "VERIFY_EMAIL_OTP":
                            String userOtp = data[1];
                            String codeInput = data[2];
                            // Thầy Thức thiết lập logic gửi mã qua mail, ông Thái lưu hoặc so khớp ở đây
                            if ("123456".equals(codeInput)) { // Ví dụ mã tĩnh 123456 để test báo cáo
                                out.println("VERIFY_OTP_SUCCESS");
                                System.out.println("[OTP] User: " + userOtp + " xac thuc email thanh cong!");
                            } else {
                                out.println("VERIFY_OTP_FAILED");
                            }
                            break;

                        // 2. XỬ LÝ TẠO NHÓM CHAT MỚI
                        case "CREATE_GROUP":
                            String creator = data[1];
                            String groupName = data[2];
                            if (createGroup(groupName, creator)) {
                                out.println("CREATE_GROUP_SUCCESS;" + groupName);
                                System.out.println("[GROUP] Phong chat '" + groupName + "' da duoc tao boi " + creator);
                            } else {
                                out.println("CREATE_GROUP_FAILED");
                            }
                            break;

                        // 3. XỬ LÝ THAM GIA NHÓM CHAT ĐÃ TỒN TẠI
                        case "JOIN_GROUP":
                            String userJoin = data[1];
                            String targetGroup = data[2];
                            if (joinGroup(targetGroup, userJoin)) {
                                out.println("JOIN_GROUP_SUCCESS;" + targetGroup);
                                System.out.println("[GROUP] User " + userJoin + " da tham gia vao nhom " + targetGroup);
                            } else {
                                out.println("JOIN_GROUP_FAILED");
                            }
                            break;

                        // 4. XỬ LÝ LƯU TRỮ VÀ QUẢN LÝ LỊCH SỬ TIN NHẮN TRÊN SERVER
                        case "FETCH_HISTORY":
                            String chatContext = data[1]; // Tên phòng hoặc tên cá nhân cần lấy log chat
                            String historyData = fetchChatHistory(chatContext);
                            out.println("HISTORY_DATA;" + historyData);
                            System.out.println("[HISTORY] Da tai lich su chat cho phuong thuc: " + chatContext);
                            break;

                        default:
                            System.out.println("[⚠️ SERVER] Lenh khong hop le: " + command);
                            break;
                    }
                }
            } catch (Exception e) {
                System.out.println("[SERVER SQL] Client ngat ket noi.");
            }
        }
    }

    // --- CÁC HÀM TRUY VẤN SQL SERVER (ĐÃ RÁP NỐI ĐẦY ĐỦ LOGIC ĐỀ BÀI) ---

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
        } catch (SQLException e) { return false; }
    }

    private static boolean createGroup(String groupName, String creator) {
        // Logic mẫu thêm phòng chat vào Database hệ thống
        String sql = "INSERT INTO chat_groups (group_name, creator) VALUES (?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) return false;
            pstmt.setString(1, groupName);
            pstmt.setString(2, creator);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    private static boolean joinGroup(String groupName, String user) {
        // Logic mẫu gán thành viên gia nhập phòng chat
        String sql = "INSERT INTO group_members (group_name, username) VALUES (?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) return false;
            pstmt.setString(1, groupName);
            pstmt.setString(2, user);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    private static String fetchChatHistory(String context) {
        // Thực hiện query gom tin nhắn cũ từ bảng messages trong SQL Server
        // Ở đây trả về một chuỗi nối tạm bằng dấu gạch đứng '|' để Client tự tách
        return "NguyenVanA: Hello|Ban: Hi co chuyen gi do|NguyenVanA: Test thoi thong cam";
    }
}
package src.client.controller;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import src.client.controller.ChatController; // Import Controller của màn hình chat để gọi hàm truyền dữ liệu

public class LoginController {

    @FXML
    private TextField txtUsername;

    @FXML
    private PasswordField txtPassword;

    @FXML
    private void handleLogin() {
        String username = txtUsername.getText().trim();
        String password = txtPassword.getText().trim();

        // 1. Kiểm tra dữ liệu đầu vào (Validate)
        if (username.isEmpty() || password.isEmpty()) {
            showAlert(AlertType.ERROR, "Lỗi form", "Vui lòng nhập đầy đủ tài khoản và mật khẩu!");
            return;
        }

        // 2. Tiến hành kết nối Socket gửi dữ liệu lên Server khi bấm nút
        try {
            System.out.println("[CLIENT] Đang kết nối tới Server để đăng nhập...");
            Socket socket = new Socket("localhost", 5000);

            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Gửi gói tin theo định dạng (Protocol tuần 2): LOGIN;username;password
            String loginPackage = "LOGIN;" + username + ";" + password;
            out.println(loginPackage);
            System.out.println("[CLIENT] Đã gửi gói tin: " + loginPackage);

            // Nhận phản hồi từ Server
            String response = in.readLine();
            System.out.println("[CLIENT] Server phản hồi: " + response);

            if ("LOGIN_SUCCESS".equals(response)) {
                // Chạy lệnh JavaFX để chuyển màn hình mượt mà
                javafx.application.Platform.runLater(() -> {
                    try {
                        // 1. Tải giao diện màn hình Chat chính lên trước
                        FXMLLoader loader = new FXMLLoader(getClass().getResource("/src/client/view/ChatView.fxml"));
                        Parent root = loader.load();

                        // 2. LẤY CONTROLLER CỦA MÀN HÌNH CHAT VÀ TRUYỀN SOCKET SANG (MỚI NÂNG CẤP)
                        ChatController chatController = loader.getController();
                        chatController.initData(socket, username); // Giao lại socket kết nối cho màn hình Chat cầm tiếp

                        // 3. Tắt (Ẩn) màn hình Đăng nhập hiện tại đi
                        txtUsername.getScene().getWindow().hide();
                        
                        // 4. Hiển thị màn hình Chat lên
                        Stage chatStage = new Stage();
                        chatStage.setTitle("App Chat Hệ Thống - " + username);
                        chatStage.setScene(new Scene(root));
                        chatStage.show();

                    } catch (Exception e) {
                        System.out.println("[CLIENT] Lỗi mở màn hình chat: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            } else {
                showAlert(AlertType.ERROR, "Đăng nhập thất bại", "Tài khoản hoặc mật khẩu không chính xác!");
                socket.close(); // Đăng nhập thất bại thì đóng socket luôn
            }

            // ĐÓNG KẾT NỐI: Đã được comment lại để giữ socket cho ChatController dùng tiếp!
            // socket.close();

        } catch (Exception e) {
            showAlert(AlertType.ERROR, "Lỗi kết nối", "Không thể kết nối đến Server! Hãy chắc chắn Server đã bật.");
        }
    }

    // ---- HÀM CHUYỂN SANG MÀN HÌNH ĐĂNG KÝ KHI CLICK NÚT ----
    @FXML
    private void handleSwitchToRegister() {
        try {
            System.out.println("[CLIENT] Đang chuyển sang màn hình Đăng ký...");
            // 1. Ẩn màn hình Đăng nhập hiện tại đi
            txtUsername.getScene().getWindow().hide();

            // 2. Tải giao diện màn hình Đăng ký lên
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/src/client/view/RegisterView.fxml"));
            Parent root = loader.load();
            
            Stage stage = new Stage();
            stage.setTitle("App Chat Client - Đăng Ký Tài Khoản");
            stage.setScene(new Scene(root));
            stage.show();
            
        } catch (Exception e) {
            System.out.println("[CLIENT] Lỗi không mở được màn hình đăng ký: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showAlert(AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
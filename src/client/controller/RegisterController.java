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

public class RegisterController {

    @FXML private TextField txtRegUsername;
    @FXML private PasswordField txtRegPassword;
    @FXML private PasswordField txtConfirmPassword;

    @FXML
    private void handleRegister() {
        String username = txtRegUsername.getText().trim();
        String password = txtRegPassword.getText().trim();
        String confirm = txtConfirmPassword.getText().trim();

        if (username.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
            showAlert(AlertType.ERROR, "Lỗi", "Vui lòng nhập đầy đủ thông tin!");
            return;
        }

        if (!password.equals(confirm)) {
            showAlert(AlertType.ERROR, "Lỗi", "Mật khẩu xác nhận không trùng khớp!");
            return;
        }

        try {
            Socket socket = new Socket("localhost", 5000);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Gửi gói tin Đăng ký: REGISTER;user;pass
            out.println("REGISTER;" + username + ";" + password);
            
            String response = in.readLine();
            if ("REGISTER_SUCCESS".equals(response)) {
                showAlert(AlertType.INFORMATION, "Thành công", "Đăng ký thành công! Hãy quay lại đăng nhập.");
                handleBackToLogin(); // Đăng ký xong tự chuyển về màn hình đăng nhập
            } else {
                showAlert(AlertType.ERROR, "Thất bại", "Tài khoản đã tồn tại trên hệ thống!");
            }
            socket.close();
        } catch (Exception e) {
            showAlert(AlertType.ERROR, "Lỗi", "Không thể kết nối đến Server!");
        }
    }

    @FXML
    private void handleBackToLogin() {
        try {
            txtRegUsername.getScene().getWindow().hide(); // Ẩn màn hình đăng ký
            Parent root = FXMLLoader.load(getClass().getResource("/src/client/view/LoginView.fxml"));
            Stage stage = new Stage();
            stage.setTitle("App Chat Client - Đăng nhập");
            stage.setScene(new Scene(root));
            stage.show();
        } catch (Exception e) {
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
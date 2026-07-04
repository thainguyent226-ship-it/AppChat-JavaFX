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
import src.client.controller.ChatController; 

public class LoginController {

    @FXML
    private TextField txtUsername;

    @FXML
    private PasswordField txtPassword;

    @FXML
    private void handleLogin() {
        String username = txtUsername.getText().trim();
        String password = txtPassword.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showAlert(AlertType.ERROR, "Lỗi form", "Vui lòng nhập đầy đủ tài khoản và mật khẩu!");
            return;
        }

        try {
            System.out.println("[CLIENT] Đang kết nối tới Server để đăng nhập...");
            Socket socket = new Socket(src.client.AppConfig.SERVER_HOST, src.client.AppConfig.SERVER_PORT);

            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            String loginPackage = "LOGIN;" + username + ";" + password;
            out.println(loginPackage);
            System.out.println("[CLIENT] Đã gửi gói tin: " + loginPackage);

            String response = in.readLine();
            System.out.println("[CLIENT] Server phản hồi: " + response);

            if ("LOGIN_SUCCESS".equals(response)) {
                javafx.application.Platform.runLater(() -> {
                    try {
                        FXMLLoader loader = new FXMLLoader(getClass().getResource("/src/client/view/ChatView.fxml"));
                        Parent root = loader.load();

                        ChatController chatController = loader.getController();
                        chatController.initData(socket, username); 

                        txtUsername.getScene().getWindow().hide();
                        
                        Stage chatStage = new Stage();
                        chatStage.setTitle("App Chat Hệ Thống - " + username);
                        chatStage.setScene(new Scene(root));
                        chatStage.show();

                    } catch (Exception e) {
                        System.out.println("[CLIENT] Lỗi mở màn hình chat: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            } else if ("LOGIN_FAILED;BLOCKED".equals(response)) {
                showAlert(AlertType.ERROR, "Tài khoản bị khóa", "Tài khoản của bạn đã bị quản trị viên khóa. Vui lòng liên hệ quản trị viên.");
                socket.close();
            } else {
                showAlert(AlertType.ERROR, "Đăng nhập thất bại", "Tài khoản hoặc mật khẩu không chính xác!");
                socket.close(); 
            }

        } catch (Exception e) {
            showAlert(AlertType.ERROR, "Lỗi kết nối", "Không thể kết nối đến Server! Hãy chắc chắn Server đã bật.");
        }
    }

    @FXML
    private void handleSwitchToRegister() {
        try {
            System.out.println("[CLIENT] Đang chuyển sang màn hình Đăng ký...");
            txtUsername.getScene().getWindow().hide();

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
package src.client.controller;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import src.client.EncryptedReader;
import src.client.EncryptedWriter;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class RegisterController {

    @FXML private TextField txtRegUsername;
    @FXML private PasswordField txtRegPassword;
    @FXML private PasswordField txtConfirmPassword;
    @FXML private TextField txtRegEmail;
    @FXML private TextField txtOtpCode;
    @FXML private Button btnSendOtp;

    @FXML
    private void handleSendOtp() {
        String email = txtRegEmail.getText().trim();

        if (email.isEmpty() || !email.contains("@") || !email.contains(".")) {
            showAlert(AlertType.WARNING, "Email không hợp lệ", "Vui lòng nhập đúng định dạng email trước khi gửi mã OTP!");
            return;
        }

        btnSendOtp.setDisable(true);
        btnSendOtp.setText("Đang gửi...");

        new Thread(() -> {
            try (Socket socket = new Socket(src.client.AppConfig.SERVER_HOST, src.client.AppConfig.SERVER_PORT)) {
                PrintWriter out = new EncryptedWriter(socket.getOutputStream());
                BufferedReader in = new EncryptedReader(socket.getInputStream());

                out.println("SEND_OTP;" + email);
                String response = in.readLine();

                Platform.runLater(() -> {
                    btnSendOtp.setDisable(false);
                    if (response != null && response.startsWith("SEND_OTP_SUCCESS")) {
                        String[] parts = response.split(";", 2);
                        String otpCode = parts.length > 1 ? parts[1] : "";
                        txtOtpCode.setText(otpCode); // tu dien san ma vao o, khong hien popup
                        btnSendOtp.setText("Đã gửi ✓");
                    } else {
                        btnSendOtp.setText("Gửi mã");
                        showAlert(AlertType.ERROR, "Thất bại",
                                "Không thể tạo mã OTP. Kiểm tra lại kết nối tới Server rồi thử lại.");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    btnSendOtp.setDisable(false);
                    btnSendOtp.setText("Gửi mã");
                    showAlert(AlertType.ERROR, "Lỗi kết nối", "Không thể kết nối đến Server! Hãy chắc chắn Server đã bật.");
                });
            }
        }).start();
    }

    @FXML
    private void handleRegister() {
        String username = txtRegUsername.getText().trim();
        String password = txtRegPassword.getText().trim();
        String confirm = txtConfirmPassword.getText().trim();
        String email = txtRegEmail.getText().trim();
        String otp = txtOtpCode.getText().trim();

        if (username.isEmpty() || password.isEmpty() || confirm.isEmpty() || email.isEmpty()) {
            showAlert(AlertType.ERROR, "Lỗi", "Vui lòng nhập đầy đủ thông tin!");
            return;
        }

        if (!password.equals(confirm)) {
            showAlert(AlertType.ERROR, "Lỗi", "Mật khẩu xác nhận không trùng khớp!");
            return;
        }

        if (otp.isEmpty()) {
            showAlert(AlertType.WARNING, "Thiếu mã OTP", "Vui lòng bấm \"Gửi mã\" và nhập mã OTP đã nhận được qua email trước khi đăng ký!");
            return;
        }

        try {
            Socket socket = new Socket(src.client.AppConfig.SERVER_HOST, src.client.AppConfig.SERVER_PORT);
            PrintWriter out = new EncryptedWriter(socket.getOutputStream());
            BufferedReader in = new EncryptedReader(socket.getInputStream());

            out.println("REGISTER;" + username + ";" + password + ";" + email + ";" + otp);

            String response = in.readLine();
            if (response != null && response.startsWith("REGISTER_SUCCESS")) {
                showAlert(AlertType.INFORMATION, "Thành công", "Đăng ký thành công! Hãy quay lại đăng nhập.");
                handleBackToLogin();
            } else if (response != null && response.startsWith("REGISTER_FAILED;EXISTS")) {
                showAlert(AlertType.ERROR, "Thất bại", "Tài khoản đã tồn tại trên hệ thống!");
            } else if (response != null && response.startsWith("REGISTER_FAILED;OTP_WRONG")) {
                showAlert(AlertType.ERROR, "Sai mã OTP", "Mã OTP không chính xác. Vui lòng kiểm tra lại email!");
            } else if (response != null && response.startsWith("REGISTER_FAILED;OTP_EXPIRED")) {
                showAlert(AlertType.ERROR, "Mã đã hết hạn", "Mã OTP đã hết hạn hoặc bạn chưa bấm \"Gửi mã\". Vui lòng gửi lại mã!");
            } else if (response != null && response.startsWith("REGISTER_FAILED;MISSING_OTP")) {
                showAlert(AlertType.WARNING, "Thiếu mã OTP", "Vui lòng bấm \"Gửi mã\" và nhập mã OTP trước khi đăng ký!");
            } else if (response != null && response.startsWith("REGISTER_FAILED;DBERROR")) {
                showAlert(AlertType.ERROR, "Lỗi cơ sở dữ liệu",
                        "Server không kết nối được tới SQL Server nên KHÔNG lưu được tài khoản.\n" +
                        "Hãy kiểm tra cửa sổ console đang chạy ServerApp để xem lỗi chi tiết.");
            } else {
                showAlert(AlertType.ERROR, "Thất bại", "Đăng ký thất bại! Phản hồi từ server: " + response);
            }
            socket.close();
        } catch (Exception e) {
            showAlert(AlertType.ERROR, "Lỗi", "Không thể kết nối đến Server!");
        }
    }

    @FXML
    private void handleBackToLogin() {
        try {
            txtRegUsername.getScene().getWindow().hide();
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
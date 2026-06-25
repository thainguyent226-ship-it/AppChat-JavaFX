package src.client.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ChatController {

    @FXML private TextArea txtChatArea;
    @FXML private TextField txtMessage;
    @FXML private ListView<String> listFriends;
    @FXML private Label lblChattingWith;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String currentUsername;

    public void initData(Socket socket, String username) {
        this.socket = socket;
        this.currentUsername = username;
        
        try {
            this.out = new PrintWriter(socket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            new Thread(new ReceiverThread()).start();
            System.out.println("[CLIENT] Đã kích hoạt luồng nghe tin nhắn ngầm.");
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void initialize() {
        listFriends.getItems().addAll("Nguyễn Văn A", "Trần Thị B", "Lê Văn C");
        
        listFriends.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                lblChattingWith.setText("💬 Đang chat với: " + newValue);
            }
        });
    }

    @FXML
    private void handleSendMessage() {
        String msg = txtMessage.getText().trim();
        String targetUser = listFriends.getSelectionModel().getSelectedItem();

        if (targetUser == null) {
            txtChatArea.appendText("[Hệ thống]: Vui lòng chọn một người trong danh sách để chat!\n");
            return;
        }

        if (!msg.isEmpty()) {
            String chatPackage = "CHAT;" + targetUser + ";" + msg;
            out.println(chatPackage);
         
            txtChatArea.appendText(" Tôi -> " + targetUser + ": " + msg + "\n");
            txtMessage.clear();
        }
    }

    // --- HÀM BẤM NÚT BẬT CỬA SỔ CẬP NHẬT THÔNG TIN CÁ NHÂN ---
    @FXML
    public void openProfileWindow(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/src/client/view/ProfileView.fxml"));
            Parent root = loader.load();

            // Truyền cổng Out (PrintWriter) và Tên tài khoản đang đăng nhập sang Popup phụ
            ProfileController controller = loader.getController();
            controller.initData(this.out, this.currentUsername);

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL); // Ép focus vào popup, không cho bấm ra ngoài màn hình chính
            stage.setTitle("Cập nhật thông tin tài khoản");
            stage.setScene(new Scene(root));
            stage.show();
        } catch (Exception e) {
            System.out.println("[ERROR] Khong the mo ProfileView.fxml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // --- HÀM XỬ LÝ ĐĂNG XUẤT QUAY VỀ MÀN HÌNH LOGIN ---
    @FXML
    public void handleLogout(ActionEvent event) {
        try {
            // 1. Đóng kết nối socket để giải phóng luồng trên Server
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }

            // 2. Tải lại giao diện đăng nhập LoginView.fxml
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/src/client/view/LoginView.fxml"));
            Parent root = loader.load();
            
            // 3. Lấy Stage hiện tại từ label tiêu đề và chuyển hướng Scene
            Stage stage = (Stage) lblChattingWith.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Đăng nhập hệ thống");
            stage.show();
            
            System.out.println("[CLIENT] Đăng xuất thành công, đã quay về màn hình Login.");
        } catch (Exception e) {
            System.out.println("[ERROR] Lỗi khi xử lý đăng xuất: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Luồng lắng nghe dữ liệu liên tục từ Server trả về
    private class ReceiverThread implements Runnable {
        @Override
        public void run() {
            try {
                String response;
                while ((response = in.readLine()) != null) {
                    final String msgFromServer = response;
                   
                    Platform.runLater(() -> {
                        // XỬ LÝ NHẬN TIN NHẮN CHAT
                        if (msgFromServer.startsWith("MESSAGE;")) {
                            String[] data = msgFromServer.split(";");
                            String fromUser = data[1];
                            String content = data[2];
                            txtChatArea.appendText(fromUser + ": " + content + "\n");
                        }
                        // XỬ LÝ PHẢN HỒI CẬP NHẬT TÀI KHOẢN THÀNH CÔNG
                        else if ("UPDATE_PROFILE_SUCCESS".equals(msgFromServer)) {
                            Alert alert = new Alert(Alert.AlertType.INFORMATION);
                            alert.setTitle("Thành công");
                            alert.setHeaderText(null);
                            alert.setContentText("Hệ thống đã lưu thông tin cá nhân mới của bạn vào SQL Server!");
                            alert.showAndWait();
                        }
                        // XỬ LÝ PHẢN HỒI CẬP NHẬT THẤT BẠI
                        else if ("UPDATE_PROFILE_FAILED".equals(msgFromServer)) {
                            Alert alert = new Alert(Alert.AlertType.ERROR);
                            alert.setTitle("Thất bại");
                            alert.setHeaderText(null);
                            alert.setContentText("Lỗi! Không thể cập nhật thông tin cá nhân.");
                            alert.showAndWait();
                        }
                    });
                }
            } catch (IOException e) {
                // Khi socket bị đóng chủ động do đăng xuất, exception sẽ rơi vào đây, in thông báo ra console là đủ
                System.out.println("[CLIENT] Ngắt kết nối lắng nghe để đăng xuất.");
            }
        }
    }
}
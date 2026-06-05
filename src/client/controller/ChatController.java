package src.client.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class ChatController {

    @FXML private TextArea txtChatArea;
    @FXML private TextField txtMessage;
    @FXML private ListView<String> listFriends;
    @FXML private Label lblChattingWith;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String currentUsername;

    // Hàm cực kỳ quan trọng để nhận dữ liệu từ màn hình Login chuyển sang
    public void initData(Socket socket, String username) {
        this.socket = socket;
        this.currentUsername = username;
        
        try {
            this.out = new PrintWriter(socket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            // Bật luồng chạy ngầm để liên tục lắng nghe tin nhắn từ Server gửi về
            new Thread(new ReceiverThread()).start();
            System.out.println("[CLIENT] Đã kích hoạt luồng nghe tin nhắn ngầm.");
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void initialize() {
        // Đổ danh sách bạn bè ảo để click chọn người chat
        listFriends.getItems().addAll("Nguyễn Văn A", "Trần Thị B", "Lê Văn C");
        
        // Bắt sự kiện khi click chọn một người trong danh sách
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
            // Định dạng gói tin chat Tuần 3: CHAT;người_nhận;nội_dung
            String chatPackage = "CHAT;" + targetUser + ";" + msg;
            out.println(chatPackage);
            
            // Hiển thị tin nhắn của chính mình lên khung chat
            txtChatArea.appendText(" " + targetUser + ": " + msg + "\n");
            txtMessage.clear();
        }
    }

    // Luồng chạy ngầm chuyên ngồi "hóng" tin nhắn từ Server đẩy xuống
    private class ReceiverThread implements Runnable {
        @Override
        public void run() {
            try {
                String response;
                while ((response = in.readLine()) != null) {
                    final String msgFromServer = response;
                    // Đẩy dữ liệu giao diện lên luồng chính JavaFX để hiển thị không bị crash
                    Platform.runLater(() -> {
                        if (msgFromServer.startsWith("MESSAGE;")) {
                            // MESSAGE;người_gửi;nội_dung
                            String[] data = msgFromServer.split(";");
                            String fromUser = data[1];
                            String content = data[2];
                            txtChatArea.appendText(fromUser + ": " + content + "\n");
                        }
                    });
                }
            } catch (IOException e) {
                Platform.runLater(() -> txtChatArea.appendText("[Hệ thống]: Mất kết nối tới Server!\n"));
            }
        }
    }
}
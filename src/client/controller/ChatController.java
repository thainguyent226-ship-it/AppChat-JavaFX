package src.client.controller;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ChatController {

    @FXML private ScrollPane scrollChat;
    @FXML private VBox vboxMessages;
    @FXML private TextField txtMessage;
    @FXML private TextField txtAddContact;
    @FXML private ListView<String> listFriends;
    @FXML private Label lblChattingWith;
    @FXML private TextField txtOtpInput;
    @FXML private TextField txtGroupInput;
    @FXML private Button btnFriendRequests;
    @FXML private Button btnAttachFile;
    @FXML private Button btnSticker;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String currentUsername;
    // Ghi nhớ những nhóm mà user hiện tại đang là thành viên, để biết gửi CHAT (1-1) hay GROUP_CHAT
    private final Set<String> myGroups = new HashSet<>();
    // Những hội thoại đang có tin nhắn chưa đọc (chưa được mở xem) -> hiện chấm đỏ
    private final Set<String> unreadChats = new HashSet<>();
    // Những bạn bè thật (đã accept) đang online -> hiện chấm xanh
    private final Set<String> onlineFriends = new HashSet<>();
    // Có lời mời kết bạn mới chưa xem hay không -> đổi màu nút chuông
    private boolean hasPendingFriendRequest = false;
    // Danh sách mã sticker demo (emoji) để chọn gửi
    private static final String[] STICKER_CODES = {"👍", "❤️", "😂", "😮", "😢", "🎉", "🔥", "👏"};
    // Bảng màu cố định để avatar cùng 1 người luôn cùng 1 màu
    private static final String[] AVATAR_COLORS = {
        "#0084ff", "#9b59b6", "#e67e22", "#16a085", "#e74c3c", "#2c3e50", "#f39c12", "#8e44ad"
    };

    public void initData(Socket socket, String username) {
        this.socket = socket;
        this.currentUsername = username;
        
        try {
            this.out = new PrintWriter(socket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            new Thread(new ReceiverThread()).start();
            out.println("GET_MY_CHATS"); // khoi phuc lai danh sach nhom + nguoi da tung chat
            System.out.println("[CLIENT] Da kich hoat luong nghe tin nhan ngam.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void initialize() {
        // Danh sách bắt đầu trống - dùng ô "Thêm liên hệ" phía trên để thêm username thật cần chat

        // Vẽ mỗi dòng trong danh sách liên hệ dạng: (avatar tròn màu, có chấm xanh online) + tên + (chấm đỏ nếu có tin chưa đọc)
        listFriends.setCellFactory(list -> new ListCell<String>() {
            private final Circle avatar = new Circle(18);
            private final Label initialLabel = new Label();
            private final Circle onlineDot = new Circle(5, Color.web("#31a24c"));
            private final StackPane avatarStack = new StackPane(avatar, initialLabel, onlineDot);
            private final Label nameLabel = new Label();
            private final Region spacer = new Region();
            private final Circle unreadDot = new Circle(5);
            private final HBox row = new HBox(10, avatarStack, nameLabel, spacer, unreadDot);
            {
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(6, 8, 6, 4));
                HBox.setHgrow(spacer, Priority.ALWAYS);
                StackPane.setAlignment(onlineDot, Pos.BOTTOM_RIGHT);
                initialLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");
                nameLabel.setStyle("-fx-text-fill: #050505; -fx-font-size: 13px; -fx-font-family: 'Segoe UI';");
                unreadDot.setFill(Color.web("#e74c3c"));
                onlineDot.setStroke(Color.WHITE);
                onlineDot.setStrokeWidth(2);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    boolean isGroup = myGroups.contains(item);
                    avatar.setFill(Color.web(isGroup ? "#7c3aed" : colorForName(item)));
                    initialLabel.setText(isGroup ? "👥" : String.valueOf(item.charAt(0)).toUpperCase());
                    initialLabel.setStyle(isGroup
                        ? "-fx-font-size: 13px;"
                        : "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");
                    onlineDot.setVisible(!isGroup && onlineFriends.contains(item));
                    boolean unread = unreadChats.contains(item);
                    nameLabel.setText(item);
                    nameLabel.setStyle("-fx-text-fill: #050505; -fx-font-size: 13px; -fx-font-weight: " +
                        (isGroup || unread ? "bold" : "normal") + "; -fx-font-family: 'Segoe UI';");
                    unreadDot.setVisible(unread);
                    setGraphic(row);
                    setStyle("-fx-background-color: transparent; -fx-padding: 2 0 2 0;");
                }
            }
        });

        listFriends.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                lblChattingWith.setText("💬" + newValue);
                clearMessages();
                unreadChats.remove(newValue);
                listFriends.refresh();
                if (out != null) {
                    out.println("FETCH_HISTORY;" + newValue);
                }
            }
        });
    }

    // Sinh 1 màu cố định cho mỗi tên (cùng 1 tên luôn ra cùng 1 màu avatar)
    private String colorForName(String name) {
        int index = Math.abs(name.hashCode()) % AVATAR_COLORS.length;
        return AVATAR_COLORS[index];
    }

    // ================= CÁC HÀM VẼ BONG BÓNG CHAT (kiểu Messenger) =================

    private void clearMessages() {
        vboxMessages.getChildren().clear();
    }

    private void scrollToBottom() {
        Platform.runLater(() -> scrollChat.setVvalue(1.0));
    }

    // isMe = true -> bong bóng xanh, nằm bên phải (tin của mình)
    // isMe = false -> bong bóng xám, nằm bên trái (tin của người khác), kèm tên người gửi phía trên
    private void addMessageBubble(String sender, String content, boolean isMe) {
        HBox row = new HBox();
        row.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        VBox bubbleBox = new VBox(2);
        bubbleBox.setMaxWidth(430);
        bubbleBox.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        if (!isMe) {
            Label nameLabel = new Label(sender);
            nameLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #8a8d91; -fx-padding: 0 0 1 12; -fx-font-family: 'Segoe UI';");
            bubbleBox.getChildren().add(nameLabel);
        }

        Label bubble = new Label(content);
        bubble.setWrapText(true);
        bubble.setMaxWidth(400);
        bubble.setStyle(isMe
            ? "-fx-background-color: linear-gradient(to right, #0084ff, #0066e6); -fx-text-fill: white; -fx-background-radius: 18; -fx-padding: 9 14 9 14; -fx-font-size: 13px; -fx-font-family: 'Segoe UI';"
            : "-fx-background-color: #e4e6eb; -fx-text-fill: #050505; -fx-background-radius: 18; -fx-padding: 9 14 9 14; -fx-font-size: 13px; -fx-font-family: 'Segoe UI';");

        bubbleBox.getChildren().add(bubble);
        row.getChildren().add(bubbleBox);
        vboxMessages.getChildren().add(row);
        scrollToBottom();
    }

    // Thông báo hệ thống hiển thị dạng viên thuốc xám căn giữa (vd: cảnh báo, thông báo lỗi nhẹ)
    private void addSystemNotice(String text) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER);
        Label notice = new Label(text);
        notice.setWrapText(true);
        notice.setStyle("-fx-background-color: #f0f2f5; -fx-text-fill: #65676b; -fx-background-radius: 12; -fx-padding: 5 14 5 14; -fx-font-size: 11px; -fx-font-family: 'Segoe UI';");
        row.getChildren().add(notice);
        vboxMessages.getChildren().add(row);
        scrollToBottom();
    }

    @FXML
    private void handleSendMessage() {
        String msg = txtMessage.getText().trim();
        String targetUser = listFriends.getSelectionModel().getSelectedItem();

        if (targetUser == null) {
            addSystemNotice("Vui lòng chọn một người trong danh sách để chat!");
            return;
        }

        if (!msg.isEmpty()) {
            String command = myGroups.contains(targetUser) ? "GROUP_CHAT" : "CHAT";
            String chatPackage = command + ";" + targetUser + ";" + msg;
            out.println(chatPackage);
            addMessageBubble(currentUsername, msg, true);
            txtMessage.clear();
        }
    }

   
    // Gửi lời mời kết bạn thật - chỉ khi người kia đồng ý (ACCEPT) thì mới xuất hiện trong danh sách của cả 2 bên
    @FXML
    private void handleAddContact() {
        String contact = txtAddContact.getText().trim();
        if (contact.isEmpty()) {
            addSystemNotice("Vui lòng nhập username cần thêm!");
            return;
        }
        if (contact.equals(currentUsername)) {
            addSystemNotice("Không thể tự thêm chính mình!");
            return;
        }
        out.println("ADD_FRIEND;" + contact);
        txtAddContact.clear();
    }

    // Mở popup danh sách lời mời kết bạn đang chờ, có nút Chấp nhận / Từ chối
    @FXML
    private void handleShowFriendRequests() {
        hasPendingFriendRequest = false;
        btnFriendRequests.setStyle("-fx-background-color: #f0f2f5; -fx-background-radius: 17; -fx-font-size: 15px; -fx-cursor: hand;");
        out.println("GET_FRIEND_REQUESTS");
    }

    private void showFriendRequestsDialog(java.util.List<String> requesters) {
        if (requesters.isEmpty()) {
            addSystemNotice("Bạn không có lời mời kết bạn nào đang chờ.");
            return;
        }
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Lời mời kết bạn");

        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        root.setStyle("-fx-background-color: white;");

        for (String requester : requesters) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            Label nameLabel = new Label(requester);
            nameLabel.setStyle("-fx-font-size: 13px; -fx-font-family: 'Segoe UI';");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Button acceptBtn = new Button("✔ Chấp nhận");
            acceptBtn.setStyle("-fx-background-color: #e7fff0; -fx-text-fill: #16a085; -fx-font-weight: bold; -fx-cursor: hand;");
            Button rejectBtn = new Button("✘ Từ chối");
            rejectBtn.setStyle("-fx-background-color: #fdeaea; -fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-cursor: hand;");
            acceptBtn.setOnAction(e -> {
                out.println("ACCEPT_FRIEND;" + requester);
                dialog.close();
            });
            rejectBtn.setOnAction(e -> {
                out.println("REJECT_FRIEND;" + requester);
                dialog.close();
            });
            row.getChildren().addAll(nameLabel, spacer, acceptBtn, rejectBtn);
            root.getChildren().add(row);
        }

        dialog.setScene(new Scene(root, 380, Math.min(400, 60 + requesters.size() * 50)));
        dialog.show();
    }

    @FXML
    private void handleCreateGroup() {
        String groupName = txtGroupInput.getText().trim();
        if (groupName.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Canh bao", "Vui long nhap ten nhom can tao!");
            return;
        }
        out.println("CREATE_GROUP;" + currentUsername + ";" + groupName);
        txtGroupInput.clear();
    }

    @FXML
    private void handleJoinGroup() {
        String groupName = txtGroupInput.getText().trim();
        if (groupName.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Canh bao", "Vui long nhap ten nhom can tham gia!");
            return;
        }
        out.println("JOIN_GROUP;" + currentUsername + ";" + groupName);
        txtGroupInput.clear();
    }

    // Gửi file đính kèm, giới hạn tối đa 1MB
    @FXML
    private void handleSendFile() {
        String targetUser = listFriends.getSelectionModel().getSelectedItem();
        if (targetUser == null) {
            addSystemNotice("Vui lòng chọn một người/nhóm để gửi file!");
            return;
        }
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn file để gửi (tối đa 1MB)");
        File file = fileChooser.showOpenDialog(txtMessage.getScene().getWindow());
        if (file == null) return;

        if (file.length() > 1_048_576) { // 1MB = 1,048,576 bytes
            showAlert(Alert.AlertType.WARNING, "File quá lớn", "File bạn chọn vượt quá 1MB. Vui lòng chọn file nhỏ hơn.");
            return;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(data)) != -1) {
                buffer.write(data, 0, bytesRead);
            }
            String base64Data = Base64.getEncoder().encodeToString(buffer.toByteArray());
            String command = myGroups.contains(targetUser) ? "FILE" : "FILE";
            out.println(command + ";" + targetUser + ";" + file.getName() + ";" + base64Data);
            addFileBubble(currentUsername, file.getName(), base64Data, true);
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không thể đọc file: " + e.getMessage());
        }
    }

    // Mở bảng chọn sticker nhỏ (emoji demo)
    @FXML
    private void handleShowStickerPicker() {
        String targetUser = listFriends.getSelectionModel().getSelectedItem();
        if (targetUser == null) {
            addSystemNotice("Vui lòng chọn một người/nhóm để gửi sticker!");
            return;
        }
        ContextMenu menu = new ContextMenu();
        for (String code : STICKER_CODES) {
            MenuItem item = new MenuItem(code);
            item.setStyle("-fx-font-size: 20px;");
            item.setOnAction(e -> {
                out.println("STICKER;" + targetUser + ";" + code);
                addStickerBubble(code, true);
            });
            menu.getItems().add(item);
        }
        menu.show(btnSticker, javafx.geometry.Side.TOP, 0, 0);
    }

    // Vẽ bong bóng file đính kèm, kèm nút Lưu file
    private void addFileBubble(String sender, String fileName, String base64Data, boolean isMe) {
        HBox row = new HBox();
        row.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        VBox bubbleBox = new VBox(4);
        bubbleBox.setMaxWidth(300);
        bubbleBox.setStyle((isMe
                ? "-fx-background-color: #e7f3ff;"
                : "-fx-background-color: #e4e6eb;") + " -fx-background-radius: 14; -fx-padding: 10 14 10 14;");

        if (!isMe) {
            Label nameLabel = new Label(sender);
            nameLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #8a8d91; -fx-font-family: 'Segoe UI';");
            bubbleBox.getChildren().add(nameLabel);
        }

        Label fileLabel = new Label("📎 " + fileName);
        fileLabel.setWrapText(true);
        fileLabel.setStyle("-fx-font-size: 13px; -fx-font-family: 'Segoe UI'; -fx-text-fill: #050505;");

        Button saveBtn = new Button("💾 Lưu file về máy");
        saveBtn.setStyle("-fx-background-color: #0084ff; -fx-text-fill: white; -fx-font-size: 11px; -fx-background-radius: 8; -fx-cursor: hand;");
        saveBtn.setOnAction(e -> saveFileToDisk(fileName, base64Data));

        bubbleBox.getChildren().addAll(fileLabel, saveBtn);
        row.getChildren().add(bubbleBox);
        vboxMessages.getChildren().add(row);
        scrollToBottom();
    }

    private void saveFileToDisk(String fileName, String base64Data) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Lưu file");
        fileChooser.setInitialFileName(fileName);
        File saveFile = fileChooser.showSaveDialog(txtMessage.getScene().getWindow());
        if (saveFile == null) return;
        try (FileOutputStream fos = new FileOutputStream(saveFile)) {
            fos.write(Base64.getDecoder().decode(base64Data));
            showAlert(Alert.AlertType.INFORMATION, "Thành công", "Đã lưu file vào: " + saveFile.getAbsolutePath());
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không thể lưu file: " + e.getMessage());
        }
    }

    // Vẽ bong bóng sticker (emoji cỡ lớn)
    private void addStickerBubble(String stickerCode, boolean isMe) {
        HBox row = new HBox();
        row.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        Label sticker = new Label(stickerCode);
        sticker.setStyle("-fx-font-size: 42px;");
        row.getChildren().add(sticker);
        vboxMessages.getChildren().add(row);
        scrollToBottom();
    }

    @FXML
    private void handleLeaveGroup() {
        String targetGroup = listFriends.getSelectionModel().getSelectedItem();
        if (targetGroup == null) {
            showAlert(Alert.AlertType.WARNING, "Canh bao", "Vui long chon nhom can roi trong danh sach!");
            return;
        }
        out.println("LEAVE_GROUP;" + currentUsername + ";" + targetGroup);
    }

    @FXML
    public void openProfileWindow(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/src/client/view/ProfileView.fxml"));
            Parent root = loader.load();

            ProfileController controller = loader.getController();
            controller.initData(this.out, this.currentUsername);

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Cập nhật thông tin tài khoản");
            stage.setScene(new Scene(root));
            stage.show();
        } catch (Exception e) {
            System.out.println("[ERROR] Khong the mo ProfileView.fxml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    public void handleLogout(ActionEvent event) {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/src/client/view/LoginView.fxml"));
            Parent root = loader.load();
            
            Stage stage = (Stage) lblChattingWith.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Đăng nhập hệ thống");
            stage.show();
            System.out.println("[CLIENT] Dang xuat thanh cong, da quay ve man hinh Login.");
        } catch (Exception e) {
            System.out.println("[ERROR] Loi khi xu ly dang xuat: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private class ReceiverThread implements Runnable {
        @Override
        public void run() {
            try {
                String response;
                while ((response = in.readLine()) != null) {
                    final String msgFromServer = response;
                   
                    Platform.runLater(() -> {
                        if (msgFromServer.startsWith("ONLINE_FRIENDS;")) {
                            String[] data = msgFromServer.split(";", 2);
                            String csv = data.length > 1 ? data[1] : "";
                            onlineFriends.clear();
                            if (!csv.isEmpty()) {
                                for (String f : csv.split(",")) {
                                    if (!f.isEmpty()) onlineFriends.add(f);
                                }
                            }
                            listFriends.refresh();
                        }
                        else if (msgFromServer.startsWith("FRIEND_ONLINE;")) {
                            String[] data = msgFromServer.split(";", 2);
                            onlineFriends.add(data[1]);
                            listFriends.refresh();
                        }
                        else if (msgFromServer.startsWith("FRIEND_OFFLINE;")) {
                            String[] data = msgFromServer.split(";", 2);
                            onlineFriends.remove(data[1]);
                            listFriends.refresh();
                        }
                        else if (msgFromServer.startsWith("FRIEND_REQUEST;")) {
                            String[] data = msgFromServer.split(";", 2);
                            hasPendingFriendRequest = true;
                            btnFriendRequests.setStyle("-fx-background-color: #fdeaea; -fx-background-radius: 17; -fx-font-size: 15px; -fx-cursor: hand;");
                            addSystemNotice("🔔 " + data[1] + " đã gửi cho bạn lời mời kết bạn. Bấm chuông để xem.");
                        }
                        else if (msgFromServer.startsWith("ADD_FRIEND_SUCCESS;")) {
                            String[] data = msgFromServer.split(";", 2);
                            addSystemNotice("Đã gửi lời mời kết bạn tới " + data[1] + ", chờ họ chấp nhận.");
                        }
                        else if (msgFromServer.startsWith("ADD_FRIEND_FAILED;")) {
                            String[] data = msgFromServer.split(";", 2);
                            String reason = data.length > 1 ? data[1] : "";
                            if ("EXISTS".equals(reason)) {
                                addSystemNotice("Đã là bạn bè hoặc đã gửi lời mời trước đó rồi.");
                            } else if ("SELF".equals(reason)) {
                                addSystemNotice("Không thể tự kết bạn với chính mình.");
                            } else {
                                addSystemNotice("Không thể gửi lời mời kết bạn lúc này.");
                            }
                        }
                        else if (msgFromServer.startsWith("FRIEND_REQUESTS;")) {
                            String[] data = msgFromServer.split(";", 2);
                            String csv = data.length > 1 ? data[1] : "";
                            java.util.List<String> requesters = new java.util.ArrayList<>();
                            if (!csv.isEmpty()) {
                                for (String r : csv.split(",")) if (!r.isEmpty()) requesters.add(r);
                            }
                            showFriendRequestsDialog(requesters);
                        }
                        else if (msgFromServer.startsWith("ACCEPT_FRIEND_SUCCESS;")) {
                            String[] data = msgFromServer.split(";", 2);
                            if (!listFriends.getItems().contains(data[1])) {
                                listFriends.getItems().add(data[1]);
                            }
                            addSystemNotice("Đã kết bạn với " + data[1] + "!");
                        }
                        else if (msgFromServer.startsWith("FRIEND_ACCEPTED;")) {
                            String[] data = msgFromServer.split(";", 2);
                            if (!listFriends.getItems().contains(data[1])) {
                                listFriends.getItems().add(data[1]);
                            }
                            addSystemNotice(data[1] + " đã chấp nhận lời mời kết bạn của bạn!");
                        }
                        else if (msgFromServer.startsWith("FILE_MSG_DM;")) {
                            String[] data = msgFromServer.split(";", 4);
                            String fromUser = data[1];
                            String fileName = data[2];
                            String base64Data = data[3];
                            String selected = listFriends.getSelectionModel().getSelectedItem();
                            if (fromUser.equals(selected)) {
                                addFileBubble(fromUser, fileName, base64Data, false);
                            } else {
                                unreadChats.add(fromUser);
                                listFriends.refresh();
                            }
                        }
                        else if (msgFromServer.startsWith("FILE_MSG;")) {
                            String[] data = msgFromServer.split(";", 5);
                            String groupNameMsg = data[1];
                            String fromUser = data[2];
                            String fileName = data[3];
                            String base64Data = data[4];
                            String selected = listFriends.getSelectionModel().getSelectedItem();
                            if (groupNameMsg.equals(selected)) {
                                addFileBubble(fromUser, fileName, base64Data, false);
                            } else {
                                unreadChats.add(groupNameMsg);
                                listFriends.refresh();
                            }
                        }
                        else if (msgFromServer.startsWith("STICKER_MSG_DM;")) {
                            String[] data = msgFromServer.split(";", 3);
                            String fromUser = data[1];
                            String code = data[2];
                            String selected = listFriends.getSelectionModel().getSelectedItem();
                            if (fromUser.equals(selected)) {
                                addStickerBubble(code, false);
                            } else {
                                unreadChats.add(fromUser);
                                listFriends.refresh();
                            }
                        }
                        else if (msgFromServer.startsWith("STICKER_MSG;")) {
                            String[] data = msgFromServer.split(";", 4);
                            String groupNameMsg = data[1];
                            String code = data[3];
                            String selected = listFriends.getSelectionModel().getSelectedItem();
                            if (groupNameMsg.equals(selected)) {
                                addStickerBubble(code, false);
                            } else {
                                unreadChats.add(groupNameMsg);
                                listFriends.refresh();
                            }
                        }
                        else if (msgFromServer.startsWith("FILE_FAILED;")) {
                            showAlert(Alert.AlertType.WARNING, "File quá lớn", "File vượt quá giới hạn 1MB, server đã từ chối nhận.");
                        }
                        else if (msgFromServer.startsWith("SYSTEM_MSG;")) {
                            String[] data = msgFromServer.split(";", 2);
                            showAlert(Alert.AlertType.INFORMATION, "📢 Thông báo hệ thống", data.length > 1 ? data[1] : "");
                        }
                        else if ("ACCOUNT_BLOCKED".equals(msgFromServer)) {
                            showAlert(Alert.AlertType.ERROR, "Tài khoản bị khóa", "Tài khoản của bạn đã bị quản trị viên khóa. Ứng dụng sẽ đăng xuất.");
                            handleLogout(null);
                        }
                        else if (msgFromServer.startsWith("MESSAGE;")) {
                            String[] data = msgFromServer.split(";", 3);
                            String fromUser = data[1];
                            String content = data[2];
                            String selected = listFriends.getSelectionModel().getSelectedItem();
                            if (!listFriends.getItems().contains(fromUser)) {
                                // Người lạ chưa có trong danh sách -> thêm vào để không mất tin nhắn
                                listFriends.getItems().add(fromUser);
                            }
                            // Chỉ vẽ bong bóng nếu đang mở đúng đoạn chat với người gửi này
                            if (fromUser.equals(selected)) {
                                addMessageBubble(fromUser, content, false);
                            } else {
                                unreadChats.add(fromUser);
                                listFriends.refresh();
                            }
                        }
                        else if (msgFromServer.startsWith("GROUP_MESSAGE;")) {
                            String[] data = msgFromServer.split(";", 4);
                            String groupName = data[1];
                            String fromUser = data[2];
                            String content = data[3];
                            String selected = listFriends.getSelectionModel().getSelectedItem();
                            if (groupName.equals(selected)) {
                                addMessageBubble(fromUser, content, false);
                            } else {
                                unreadChats.add(groupName);
                                listFriends.refresh();
                            }
                        }
                        else if (msgFromServer.startsWith("MY_CHATS;")) {
                            String[] data = msgFromServer.split(";", 3);
                            String groupsCsv = data.length > 1 ? data[1] : "";
                            String contactsCsv = data.length > 2 ? data[2] : "";
                            if (!groupsCsv.isEmpty()) {
                                for (String g : groupsCsv.split(",")) {
                                    if (!g.isEmpty()) {
                                        myGroups.add(g);
                                        if (!listFriends.getItems().contains(g)) {
                                            listFriends.getItems().add(g);
                                        }
                                    }
                                }
                            }
                            if (!contactsCsv.isEmpty()) {
                                for (String c : contactsCsv.split(",")) {
                                    if (!c.isEmpty() && !listFriends.getItems().contains(c)) {
                                        listFriends.getItems().add(c);
                                    }
                                }
                            }
                            listFriends.refresh();
                        }
                        else if (msgFromServer.startsWith("HISTORY_DATA;")) {
                            String[] data = msgFromServer.split(";", 3);
                            String context = data.length > 1 ? data[1] : "";
                            String content = data.length > 2 ? data[2] : "";
                            // Chỉ hiển thị nếu vẫn đang chọn đúng hội thoại đó (tránh đè nhầm khi user bấm chuyển qua lại nhanh)
                            String selected = listFriends.getSelectionModel().getSelectedItem();
                            if (context.equals(selected)) {
                                clearMessages();
                                if (!content.isEmpty()) {
                                    for (String line : content.split("\\|")) {
                                        String[] parts = line.split(": ", 2);
                                        String sender = parts.length > 0 ? parts[0] : "";
                                        String text = parts.length > 1 ? parts[1] : line;
                                        addMessageBubble(sender, text, sender.equals(currentUsername));
                                    }
                                } else {
                                    addSystemNotice("Chưa có tin nhắn nào. Hãy bắt đầu cuộc trò chuyện!");
                                }
                            }
                        }
                        else if ("UPDATE_PROFILE_SUCCESS".equals(msgFromServer)) {
                            showAlert(Alert.AlertType.INFORMATION, "Thanh cong", "He thong da luu thong tin ca nhan moi vao SQL Server!");
                        }
                        else if ("UPDATE_PROFILE_FAILED".equals(msgFromServer)) {
                            showAlert(Alert.AlertType.ERROR, "That bai", "Loi! Khong the cap nhat thong tin ca nhan.");
                        }
                        else if ("VERIFY_OTP_SUCCESS".equals(msgFromServer)) {
                            showAlert(Alert.AlertType.INFORMATION, "Thanh cong", "Xac thuc email thanh cong!");
                        }
                        else if ("VERIFY_OTP_FAILED".equals(msgFromServer)) {
                            showAlert(Alert.AlertType.ERROR, "That bai", "Ma OTP khong chinh xac.");
                        }
                        else if (msgFromServer.startsWith("CREATE_GROUP_SUCCESS;")) {
                            String[] data = msgFromServer.split(";");
                            listFriends.getItems().add(data[1]);
                            myGroups.add(data[1]);
                            showAlert(Alert.AlertType.INFORMATION, "Thanh cong", "Da tao va gia nhap nhom: " + data[1]);
                        }
                        else if ("CREATE_GROUP_FAILED".equals(msgFromServer)) {
                            showAlert(Alert.AlertType.ERROR, "That bai", "Loi! Khong the tao nhom moi.");
                        }
                        else if (msgFromServer.startsWith("JOIN_GROUP_SUCCESS;")) {
                            String[] data = msgFromServer.split(";");
                            if (!listFriends.getItems().contains(data[1])) {
                                listFriends.getItems().add(data[1]);
                            }
                            myGroups.add(data[1]);
                            showAlert(Alert.AlertType.INFORMATION, "Thanh cong", "Da tham gia vao nhom: " + data[1]);
                        }
                        else if ("JOIN_GROUP_FAILED".equals(msgFromServer)) {
                            showAlert(Alert.AlertType.ERROR, "That bai", "Loi! Khong the tham gia nhom nay.");
                        }
                        else if (msgFromServer.startsWith("LEAVE_GROUP_SUCCESS;")) {
                            String[] data = msgFromServer.split(";");
                            listFriends.getItems().remove(data[1]);
                            myGroups.remove(data[1]);
                            lblChattingWith.setText("💬 Đang chat với: Bạn bè");
                            showAlert(Alert.AlertType.INFORMATION, "Thanh cong", "Da roi khoi nhom: " + data[1]);
                        }
                        else if ("LEAVE_GROUP_FAILED".equals(msgFromServer)) {
                            showAlert(Alert.AlertType.ERROR, "That bai", "Loi! Khong the roi khoi nhom.");
                        }
                    });
                }
            } catch (IOException e) {
                System.out.println("[CLIENT] Ngat ket noi lang nghe de dang xuat.");
            }
        }
    }
}
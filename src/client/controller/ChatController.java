package src.client.controller;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import src.client.EncryptedReader;
import src.client.EncryptedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.transform.Rotate;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.Stage;

public class ChatController {

    @FXML private ScrollPane scrollChat;
    @FXML private VBox vboxMessages;
    @FXML private TextField txtMessage;
    @FXML private TextField txtAddContact;
    @FXML private ListView<String> listFriends;
    @FXML private Label lblChattingWith;
    @FXML private TextField txtOtpInput;
    @FXML private Button btnGroupOptions;
    @FXML private Button btnFriendRequests;
    @FXML private Button btnAttachFile;
    @FXML private Button btnSticker;
    @FXML private Button btnSettings;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String currentUsername;
    private ProfileController activeProfileController;
    private final Set<String> myGroups = new HashSet<>();
    private final Set<String> unreadChats = new HashSet<>();
    
    private final Map<String, String> displayNames = new HashMap<>();
    private final Set<String> onlineFriends = new HashSet<>();
    private Label currentStatusLabel;
    private String currentStatusContext;
    private boolean hasPendingFriendRequest = false;
    private static final String[] STICKER_CODES = {"smile", "sad", "heart", "star", "like", "fire"};
    private static final String[] AVATAR_COLORS = {
        "#0084ff", "#9b59b6", "#e67e22", "#16a085", "#e74c3c", "#2c3e50", "#f39c12", "#8e44ad"
    };

    public void initData(Socket socket, String username) {
        this.socket = socket;
        this.currentUsername = username;
        
        try {
            this.out = new EncryptedWriter(socket.getOutputStream());
            this.in = new EncryptedReader(socket.getInputStream());
            new Thread(new ReceiverThread()).start();
            out.println("GET_MY_CHATS"); // khoi phuc lai danh sach nhom + nguoi da tung chat
            System.out.println("[CLIENT] Da kich hoat luong nghe tin nhan ngam.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void initialize() {

        btnSettings.setGraphic(buildGearIcon(20));

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
                    String shownName = isGroup ? item : displayNames.getOrDefault(item, item);
                    avatar.setFill(Color.web(isGroup ? "#7c3aed" : colorForName(item)));
                    initialLabel.setText(isGroup ? "G" : String.valueOf(shownName.charAt(0)).toUpperCase());
                    initialLabel.setStyle(isGroup
                        ? "-fx-font-size: 13px;"
                        : "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");
                    onlineDot.setVisible(!isGroup && onlineFriends.contains(item));
                    boolean unread = unreadChats.contains(item);
                    nameLabel.setText(shownName);
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
                lblChattingWith.setText("Đang chat với: " + (myGroups.contains(newValue) ? newValue : displayNames.getOrDefault(newValue, newValue)));
                clearMessages();
                unreadChats.remove(newValue);
                listFriends.refresh();
                if (out != null) {
                    out.println("FETCH_HISTORY;" + newValue);
                }
            }
        });
    }

    private String colorForName(String name) {
        int index = Math.abs(name.hashCode()) % AVATAR_COLORS.length;
        return AVATAR_COLORS[index];
    }

    private void clearMessages() {
        vboxMessages.getChildren().clear();
    }

    private void scrollToBottom() {
        Platform.runLater(() -> scrollChat.setVvalue(1.0));
    }

    private Label addMessageBubble(String sender, String content, boolean isMe, String statusText) {
        HBox row = new HBox();
        row.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        VBox bubbleBox = new VBox(2);
        bubbleBox.setMaxWidth(430);
        bubbleBox.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        if (!isMe) {
            Label nameLabel = new Label(displayNames.getOrDefault(sender, sender));
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

        Label statusLabel = null;
        if (isMe && statusText != null) {
            statusLabel = new Label(statusText);
            statusLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #8a8d91; -fx-padding: 2 4 0 0; -fx-font-family: 'Segoe UI';");
            bubbleBox.getChildren().add(statusLabel);
        }

        row.getChildren().add(bubbleBox);
        vboxMessages.getChildren().add(row);
        scrollToBottom();
        return statusLabel;
    }

    private String statusLabelText(String status) {
        switch (status) {
            case "SENT": return "Đã gửi";
            case "DELIVERED": return "Đã nhận";
            case "SEEN": return "Đã xem";
            default: return "";
        }
    }

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
            if ("CHAT".equals(command)) {
                currentStatusContext = targetUser;
                currentStatusLabel = addMessageBubble(currentUsername, msg, true, "Đã gửi");
            } else {
                addMessageBubble(currentUsername, msg, true, null);
            }
            txtMessage.clear();
        }
    }

   
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
            Button acceptBtn = new Button("Chấp nhận");
            acceptBtn.setStyle("-fx-background-color: #e7fff0; -fx-text-fill: #16a085; -fx-font-weight: bold; -fx-cursor: hand;");
            Button rejectBtn = new Button("Từ chối");
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
    private void handleShowGroupDialog() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Tạo nhóm chat");

        VBox root = new VBox(14);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: white;");

        Label title = new Label("Tạo nhóm chat mới");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI'; -fx-text-fill: #050505;");

        TextField txtName = new TextField();
        txtName.setPromptText("Nhập tên nhóm...");
        txtName.setPrefHeight(38);
        txtName.setStyle("-fx-background-color: #f0f2f5; -fx-background-radius: 8; -fx-font-size: 13px; -fx-border-color: transparent;");

        Button createBtn = new Button("Tạo nhóm mới");
        createBtn.setMaxWidth(Double.MAX_VALUE);
        createBtn.setPrefHeight(38);
        createBtn.setStyle("-fx-background-color: #0084ff; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 10; -fx-cursor: hand; -fx-font-size: 13px;");
        createBtn.setOnAction(e -> {
            String name = txtName.getText().trim();
            if (name.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Vui lòng nhập tên nhóm cần tạo!");
                return;
            }
            out.println("CREATE_GROUP;" + currentUsername + ";" + name);
            dialog.close();
        });

        root.getChildren().addAll(title, txtName, createBtn);
        dialog.setScene(new Scene(root, 340, 210));
        dialog.show();
    }

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

    @FXML
    private void handleShowStickerPicker() {
        String targetUser = listFriends.getSelectionModel().getSelectedItem();
        if (targetUser == null) {
            addSystemNotice("Vui lòng chọn một người/nhóm để gửi sticker!");
            return;
        }

        Popup popup = new Popup();
        popup.setAutoHide(true);

        GridPane grid = new GridPane();
        grid.setHgap(4);
        grid.setVgap(4);
        grid.setPadding(new Insets(10));
        grid.setStyle("-fx-background-color: white; -fx-background-radius: 16; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 14, 0, 0, 4);");

        int col = 0, row = 0;
        for (String stickerId : STICKER_CODES) {
            StackPane cell = new StackPane(createStickerNode(stickerId, 40));
            cell.setPrefSize(46, 46);
            String normalStyle = "-fx-background-color: transparent; -fx-cursor: hand; -fx-background-radius: 10;";
            String hoverStyle = "-fx-background-color: #f0f2f5; -fx-cursor: hand; -fx-background-radius: 10;";
            cell.setStyle(normalStyle);
            cell.setOnMouseEntered(e -> cell.setStyle(hoverStyle));
            cell.setOnMouseExited(e -> cell.setStyle(normalStyle));
            cell.setOnMouseClicked(e -> {
                out.println("STICKER;" + targetUser + ";" + stickerId);
                addStickerBubble(stickerId, true);
                popup.hide();
            });
            grid.add(cell, col, row);
            col++;
            if (col == 4) { col = 0; row++; }
        }

        popup.getContent().add(grid);
        Bounds bounds = btnSticker.localToScreen(btnSticker.getBoundsInLocal());
        popup.show(btnSticker, bounds.getMinX() - 90, bounds.getMinY() - 140);
    }

    private void addFileBubble(String sender, String fileName, String base64Data, boolean isMe) {
        HBox row = new HBox();
        row.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        VBox bubbleBox = new VBox(4);
        bubbleBox.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        if (!isMe) {
            Label nameLabel = new Label(displayNames.getOrDefault(sender, sender));
            nameLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #8a8d91; -fx-padding: 0 0 1 12; -fx-font-family: 'Segoe UI';");
            bubbleBox.getChildren().add(nameLabel);
        }

        String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase() : "";
        boolean isImage = ext.matches("png|jpg|jpeg|gif|bmp|webp");

        if (isImage) {
            try {
                byte[] imgBytes = Base64.getDecoder().decode(base64Data);
                Image image = new Image(new ByteArrayInputStream(imgBytes));
                double fitWidth = Math.min(240, image.getWidth());
                double fitHeight = fitWidth * (image.getHeight() / image.getWidth());

                ImageView imageView = new ImageView(image);
                imageView.setFitWidth(fitWidth);
                imageView.setFitHeight(fitHeight);
                imageView.setSmooth(true);
                imageView.setPreserveRatio(true);

                Rectangle clip = new Rectangle(fitWidth, fitHeight);
                clip.setArcWidth(18);
                clip.setArcHeight(18);
                imageView.setClip(clip);
                imageView.setStyle("-fx-cursor: hand;");
                imageView.setOnMouseClicked(e -> saveFileToDisk(fileName, base64Data));

                bubbleBox.getChildren().add(imageView);
            } catch (Exception ex) {
                bubbleBox.getChildren().add(buildFileCard(fileName, base64Data, isMe));
            }
        } else {
            bubbleBox.getChildren().add(buildFileCard(fileName, base64Data, isMe));
        }

        row.getChildren().add(bubbleBox);
        vboxMessages.getChildren().add(row);
        scrollToBottom();
    }

    private HBox buildFileCard(String fileName, String base64Data, boolean isMe) {
        String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase() : "";

        HBox card = new HBox(10);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMaxWidth(280);
        card.setStyle((isMe ? "-fx-background-color: #e7f3ff;" : "-fx-background-color: #f0f2f5;") +
                " -fx-background-radius: 14; -fx-padding: 10 12 10 12;");

        Label iconLabel = new Label(fileIconFor(ext));
        iconLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: white; -fx-background-color: #0084ff; " +
                "-fx-background-radius: 6; -fx-padding: 4 6 4 6; -fx-font-family: 'Segoe UI';");

        VBox textBox = new VBox(2);
        Label nameLabel = new Label(fileName);
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(150);
        nameLabel.setStyle("-fx-font-size: 12.5px; -fx-font-family: 'Segoe UI'; -fx-text-fill: #050505; -fx-font-weight: bold;");
        Label sizeLabel = new Label(formatFileSize(base64Data));
        sizeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #65676b; -fx-font-family: 'Segoe UI';");
        textBox.getChildren().addAll(nameLabel, sizeLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button downloadBtn = new Button("Tải");
        downloadBtn.setPrefSize(44, 28);
        downloadBtn.setStyle("-fx-background-color: #0084ff; -fx-text-fill: white; -fx-background-radius: 14; -fx-font-weight: bold; -fx-font-size: 10.5px; -fx-cursor: hand;");
        downloadBtn.setOnAction(e -> saveFileToDisk(fileName, base64Data));

        card.getChildren().addAll(iconLabel, textBox, spacer, downloadBtn);
        return card;
    }

    private String fileIconFor(String ext) {
        switch (ext) {
            case "pdf": return "PDF";
            case "doc": case "docx": return "DOC";
            case "xls": case "xlsx": return "XLS";
            case "ppt": case "pptx": return "PPT";
            case "zip": case "rar": case "7z": return "ZIP";
            case "mp3": case "wav": case "m4a": return "MP3";
            case "mp4": case "avi": case "mov": case "mkv": return "MP4";
            case "txt": return "TXT";
            default: return "FILE";
        }
    }

    private String formatFileSize(String base64Data) {
        long approxBytes = (long) (base64Data.length() * 0.75);
        if (approxBytes < 1024) return approxBytes + " B";
        if (approxBytes < 1024 * 1024) return String.format("%.1f KB", approxBytes / 1024.0);
        return String.format("%.2f MB", approxBytes / (1024.0 * 1024.0));
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

    private Node buildGearIcon(double size) {
        double cx = size / 2, cy = size / 2;
        double bodyR = size * 0.30;
        double toothW = size * 0.17, toothH = size * 0.20;

        Group teeth = new Group();
        for (int i = 0; i < 8; i++) {
            Rectangle tooth = new Rectangle(-toothW / 2, -bodyR - toothH * 0.6, toothW, toothH);
            tooth.setFill(Color.web("#65676b"));
            tooth.setArcWidth(2);
            tooth.setArcHeight(2);
            tooth.getTransforms().add(new Rotate(i * 45, 0, 0));
            teeth.getChildren().add(tooth);
        }
        teeth.setTranslateX(cx);
        teeth.setTranslateY(cy);

        Circle body = new Circle(cx, cy, bodyR, Color.web("#65676b"));
        Circle hole = new Circle(cx, cy, bodyR * 0.42, Color.web("#f0f2f5"));

        Pane pane = new Pane(teeth, body, hole);
        pane.setPrefSize(size, size);
        return pane;
    }

    private void addStickerBubble(String stickerId, boolean isMe) {
        HBox row = new HBox();
        row.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        Node sticker = createStickerNode(stickerId, 72);
        sticker.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 6, 0, 0, 3);");
        row.getChildren().add(sticker);
        vboxMessages.getChildren().add(row);
        scrollToBottom();
    }

    private Node createStickerNode(String stickerId, double size) {
        switch (stickerId) {
            case "smile": return buildFaceSticker(size, Color.web("#f1c40f"), true);
            case "sad": return buildFaceSticker(size, Color.web("#5dade2"), false);
            case "heart": return buildHeartSticker(size);
            case "star": return buildStarSticker(size);
            case "like": return buildLikeSticker(size);
            case "fire": return buildFireSticker(size);
            default: return buildFaceSticker(size, Color.web("#f1c40f"), true);
        }
    }

    private Node buildFaceSticker(double size, Color color, boolean happy) {
        Pane pane = new Pane();
        pane.setPrefSize(size, size);
        Circle face = new Circle(size / 2, size / 2, size / 2 - 2, color);
        face.setStroke(color.darker());
        face.setStrokeWidth(1.5);
        Circle leftEye = new Circle(size * 0.35, size * 0.42, size * 0.06, Color.web("#2c3e50"));
        Circle rightEye = new Circle(size * 0.65, size * 0.42, size * 0.06, Color.web("#2c3e50"));
        Arc mouth = new Arc(size * 0.5, happy ? size * 0.55 : size * 0.68, size * 0.22, size * 0.16,
                happy ? 200 : 20, happy ? 140 : -140);
        mouth.setType(ArcType.OPEN);
        mouth.setFill(Color.TRANSPARENT);
        mouth.setStroke(Color.web("#2c3e50"));
        mouth.setStrokeWidth(size * 0.05);
        mouth.setStrokeLineCap(StrokeLineCap.ROUND);
        pane.getChildren().addAll(face, leftEye, rightEye, mouth);
        return pane;
    }

    private Node buildHeartSticker(double size) {
        Pane pane = new Pane();
        pane.setPrefSize(size, size);
        double r = size * 0.28;
        Circle left = new Circle(size * 0.35, size * 0.38, r, Color.web("#e74c3c"));
        Circle right = new Circle(size * 0.65, size * 0.38, r, Color.web("#e74c3c"));
        Polygon bottom = new Polygon(
                size * 0.10, size * 0.42,
                size * 0.90, size * 0.42,
                size * 0.50, size * 0.94
        );
        bottom.setFill(Color.web("#e74c3c"));
        pane.getChildren().addAll(left, right, bottom);
        return pane;
    }

    private Node buildStarSticker(double size) {
        Pane pane = new Pane();
        pane.setPrefSize(size, size);
        Polygon star = new Polygon();
        double cx = size / 2, cy = size / 2;
        double outerR = size / 2 - 2, innerR = outerR * 0.45;
        for (int i = 0; i < 10; i++) {
            double angle = Math.PI / 2 + i * Math.PI / 5;
            double r = (i % 2 == 0) ? outerR : innerR;
            star.getPoints().addAll(cx + r * Math.cos(angle), cy - r * Math.sin(angle));
        }
        star.setFill(Color.web("#f1c40f"));
        star.setStroke(Color.web("#f39c12"));
        star.setStrokeWidth(1.5);
        pane.getChildren().add(star);
        return pane;
    }

    private Node buildLikeSticker(double size) {
        Pane pane = new Pane();
        pane.setPrefSize(size, size);
        Circle bg = new Circle(size / 2, size / 2, size / 2 - 2, Color.web("#3498db"));
        Polyline check = new Polyline(
                size * 0.28, size * 0.52,
                size * 0.44, size * 0.68,
                size * 0.74, size * 0.32
        );
        check.setStroke(Color.WHITE);
        check.setStrokeWidth(size * 0.08);
        check.setStrokeLineCap(StrokeLineCap.ROUND);
        check.setStrokeLineJoin(StrokeLineJoin.ROUND);
        pane.getChildren().addAll(bg, check);
        return pane;
    }

    private Node buildFireSticker(double size) {
        Pane pane = new Pane();
        pane.setPrefSize(size, size);
        Path flame = new Path();
        flame.getElements().add(new MoveTo(size * 0.5, size * 0.05));
        flame.getElements().add(new CubicCurveTo(size * 0.9, size * 0.35, size * 0.78, size * 0.6, size * 0.6, size * 0.58));
        flame.getElements().add(new CubicCurveTo(size * 0.72, size * 0.42, size * 0.52, size * 0.38, size * 0.55, size * 0.55));
        flame.getElements().add(new CubicCurveTo(size * 0.58, size * 0.7, size * 0.28, size * 0.68, size * 0.28, size * 0.88));
        flame.getElements().add(new CubicCurveTo(size * 0.05, size * 0.68, size * 0.12, size * 0.28, size * 0.5, size * 0.05));
        flame.setFill(Color.web("#e67e22"));
        flame.setStroke(Color.web("#d35400"));
        flame.setStrokeWidth(1);
        pane.getChildren().add(flame);
        return pane;
    }

    @FXML
    private void handleShowSettingsMenu(ActionEvent event) {
        ContextMenu menu = new ContextMenu();
        menu.setStyle("-fx-font-family: 'Segoe UI';");

        String selected = listFriends.getSelectionModel().getSelectedItem();
        boolean viewingGroup = selected != null && myGroups.contains(selected);

        if (viewingGroup) {
            MenuItem addMemberItem = new MenuItem("+  Thêm thành viên vào nhóm");
            addMemberItem.setOnAction(e -> handleInviteToGroup(selected));
            menu.getItems().add(addMemberItem);
        }

        MenuItem leaveGroupItem = new MenuItem("Rời nhóm");
        leaveGroupItem.setOnAction(e -> handleLeaveGroup());

        MenuItem editProfileItem = new MenuItem("Sửa thông tin");
        editProfileItem.setOnAction(e -> openProfileWindow(event));

        MenuItem logoutItem = new MenuItem("Đăng xuất");
        logoutItem.setOnAction(e -> handleLogout(event));

        menu.getItems().addAll(leaveGroupItem, editProfileItem, new SeparatorMenuItem(), logoutItem);
        menu.show(btnSettings, javafx.geometry.Side.TOP, 0, 0);
    }

    private void handleInviteToGroup(String groupName) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Thêm thành viên");
        dialog.setHeaderText(null);
        dialog.setContentText("Nhập username cần thêm vào nhóm \"" + groupName + "\":");
        dialog.showAndWait().ifPresent(username -> {
            String target = username.trim();
            if (!target.isEmpty()) {
                out.println("INVITE_TO_GROUP;" + groupName + ";" + target);
            }
        });
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
            this.activeProfileController = controller;
            out.println("GET_PROFILE;" + currentUsername);

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Cập nhật thông tin tài khoản");
            stage.setScene(new Scene(root));
            stage.setOnHidden(e -> activeProfileController = null);
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
                        if (msgFromServer.startsWith("INVITE_SUCCESS;")) {
                            String[] data = msgFromServer.split(";", 3);
                            addSystemNotice("Đã thêm " + data[2] + " vào nhóm \"" + data[1] + "\".");
                        }
                        else if (msgFromServer.startsWith("INVITE_FAILED;")) {
                            String[] data = msgFromServer.split(";", 2);
                            showAlert(Alert.AlertType.ERROR, "Thất bại", "Không thể thêm người này vào nhóm \"" + data[1] + "\" (có thể đã là thành viên hoặc username không tồn tại).");
                        }
                        else if (msgFromServer.startsWith("ADDED_TO_GROUP;")) {
                            String[] data = msgFromServer.split(";", 2);
                            String groupName = data[1];
                            myGroups.add(groupName);
                            if (!listFriends.getItems().contains(groupName)) {
                                listFriends.getItems().add(groupName);
                            }
                            listFriends.refresh();
                            addSystemNotice("Bạn vừa được thêm vào nhóm \"" + groupName + "\"!");
                        }
                        else if (msgFromServer.startsWith("ONLINE_FRIENDS;")) {
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
                            String selected = listFriends.getSelectionModel().getSelectedItem();
                            if (data[1].equals(selected)) {
                                addSystemNotice(data[1] + " vừa online.");
                            }
                        }
                        else if (msgFromServer.startsWith("FRIEND_OFFLINE;")) {
                            String[] data = msgFromServer.split(";", 2);
                            onlineFriends.remove(data[1]);
                            listFriends.refresh();
                            String selected = listFriends.getSelectionModel().getSelectedItem();
                            if (data[1].equals(selected)) {
                                addSystemNotice(data[1] + " vừa offline.");
                            }
                        }
                        else if (msgFromServer.startsWith("FRIEND_REQUEST;")) {
                            String[] data = msgFromServer.split(";", 2);
                            hasPendingFriendRequest = true;
                            btnFriendRequests.setStyle("-fx-background-color: #fdeaea; -fx-background-radius: 17; -fx-font-size: 15px; -fx-cursor: hand;");
                            addSystemNotice(data[1] + " đã gửi cho bạn lời mời kết bạn. Bấm vào nút @ để xem.");
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
                            out.println("GET_DISPLAY_NAMES;" + data[1]);
                            addSystemNotice("Đã kết bạn với " + data[1] + "!");
                        }
                        else if (msgFromServer.startsWith("FRIEND_ACCEPTED;")) {
                            String[] data = msgFromServer.split(";", 2);
                            if (!listFriends.getItems().contains(data[1])) {
                                listFriends.getItems().add(data[1]);
                            }
                            out.println("GET_DISPLAY_NAMES;" + data[1]);
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
                            showAlert(Alert.AlertType.INFORMATION, "» Thông báo hệ thống", data.length > 1 ? data[1] : "");
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
                                listFriends.getItems().add(fromUser);
                                out.println("GET_DISPLAY_NAMES;" + fromUser);
                            }
                            if (fromUser.equals(selected)) {
                                addMessageBubble(fromUser, content, false, null);
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
                                addMessageBubble(fromUser, content, false, null);
                            } else {
                                unreadChats.add(groupName);
                                listFriends.refresh();
                            }
                        }
                        else if (msgFromServer.startsWith("DISPLAY_NAMES;")) {
                            String[] data = msgFromServer.split(";", 2);
                            String csv = data.length > 1 ? data[1] : "";
                            if (!csv.isEmpty()) {
                                for (String pair : csv.split(",")) {
                                    String[] kv = pair.split("::", 2);
                                    if (kv.length == 2 && !kv[0].isEmpty()) {
                                        displayNames.put(kv[0], kv[1].isEmpty() ? kv[0] : kv[1]);
                                    }
                                }
                                listFriends.refresh();
                                String selected = listFriends.getSelectionModel().getSelectedItem();
                                if (selected != null && !myGroups.contains(selected)) {
                                    lblChattingWith.setText("Đang chat với: " + displayNames.getOrDefault(selected, selected));
                                }
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
                                out.println("GET_DISPLAY_NAMES;" + contactsCsv);
                            }
                            listFriends.refresh();
                        }
                        else if (msgFromServer.startsWith("HISTORY_DATA;")) {
                            String[] data = msgFromServer.split(";", 4);
                            String context = data.length > 1 ? data[1] : "";
                            String content = data.length > 2 ? data[2] : "";
                            String myLastStatus = data.length > 3 ? data[3] : "NONE";
                            String selected = listFriends.getSelectionModel().getSelectedItem();
                            if (context.equals(selected)) {
                                clearMessages();
                                currentStatusLabel = null;
                                currentStatusContext = context;
                                if (!content.isEmpty()) {
                                    String[] lines = content.split("\\|");
                                    for (int i = 0; i < lines.length; i++) {
                                        String line = lines[i];
                                        String[] parts = line.split(": ", 2);
                                        String sender = parts.length > 0 ? parts[0] : "";
                                        String text = parts.length > 1 ? parts[1] : line;
                                        boolean isMe = sender.equals(currentUsername);
                                        boolean isLast = (i == lines.length - 1);
                                        if (text.startsWith("[Sticker] ")) {
                                            addStickerBubble(text.substring(10).trim(), isMe);
                                        } else if (text.startsWith("[File] ")) {
                                            addSystemNotice((isMe ? "Bạn" : sender) + " đã gửi file: " + text.substring(7).trim() + " (lịch sử cũ, không tải lại được)");
                                        } else if (isMe && isLast && !"NONE".equals(myLastStatus)) {
                                            currentStatusLabel = addMessageBubble(sender, text, true, statusLabelText(myLastStatus));
                                        } else {
                                            addMessageBubble(sender, text, isMe, null);
                                        }
                                    }
                                } else {
                                    addSystemNotice("Chưa có tin nhắn nào. Hãy bắt đầu cuộc trò chuyện!");
                                }
                            }
                        }
                        else if (msgFromServer.startsWith("MSG_STATUS;")) {
                            String[] data = msgFromServer.split(";", 3);
                            String context = data.length > 1 ? data[1] : "";
                            String status = data.length > 2 ? data[2] : "";
                            if (context.equals(currentStatusContext) && currentStatusLabel != null) {
                                currentStatusLabel.setText(statusLabelText(status));
                            }
                        }
                        else if ("UPDATE_PROFILE_SUCCESS".equals(msgFromServer)) {
                            if (activeProfileController != null) {
                                activeProfileController.onSaveSuccess();
                            } else {
                                showAlert(Alert.AlertType.INFORMATION, "Thành công", "Hệ thống đã lưu thông tin cá nhân mới!");
                            }
                        }
                        else if ("UPDATE_PROFILE_FAILED".equals(msgFromServer)) {
                            if (activeProfileController != null) {
                                activeProfileController.onSaveFailed();
                            } else {
                                showAlert(Alert.AlertType.ERROR, "Thất bại", "Lỗi! Không thể cập nhật thông tin cá nhân.");
                            }
                        }
                        else if (msgFromServer.startsWith("PROFILE_DATA;")) {
                            String[] data = msgFromServer.split(";", -1);
                            if (activeProfileController != null && data.length >= 7) {
                                activeProfileController.populateFields(data[1], data[2], data[3], data[4], data[5], data[6]);
                            }
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
                        else if (msgFromServer.startsWith("CREATE_GROUP_FAILED;")) {
                            String[] data = msgFromServer.split(";", 2);
                            String reason = data.length > 1 ? data[1] : "";
                            if ("EXISTS".equals(reason)) {
                                showAlert(Alert.AlertType.WARNING, "Tên nhóm đã tồn tại", "Tên nhóm này đã có người tạo trước rồi. Vui lòng chọn 1 tên khác.");
                            } else {
                                showAlert(Alert.AlertType.ERROR, "Lỗi cơ sở dữ liệu", "Server không kết nối được tới SQL Server. Kiểm tra console ServerApp để xem chi tiết.");
                            }
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
                            lblChattingWith.setText("Đang chat với: Bạn bè");
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
package src.client.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import java.io.PrintWriter;

public class ProfileController {
    @FXML private TextField txtFullName;
    @FXML private DatePicker datePickerDob;
    @FXML private TextField txtUniversity;
    
    // --- BẮT BUỘC PHẢI KHAI BÁO 2 BIẾN NÀY ĐỂ ĐỒNG BỘ VỚI FXML MỚI ---
    @FXML private TextField txtEmail;
    @FXML private TextField txtPhone;

    private PrintWriter out;
    private String currentUsername;

    // Hàm nhận luồng dữ liệu từ màn hình Chat truyền sang
    public void initData(PrintWriter out, String username) {
        this.out = out;
        this.currentUsername = username;
    }

    @FXML
    public void handleSaveProfile(ActionEvent event) {
        String fullName = txtFullName.getText().trim();
        String dob = (datePickerDob.getValue() != null) ? datePickerDob.getValue().toString() : "";
        String university = txtUniversity.getText().trim();
        
        // --- LẤY DỮ LIỆU TỪ 2 Ô MỚI THÊM TRÊN GIAO DIỆN ---
        String email = txtEmail.getText().trim();
        String phone = txtPhone.getText().trim();

        // Kiểm tra không cho để trống bất kỳ ô nào
        if (fullName.isEmpty() || dob.isEmpty() || university.isEmpty() || email.isEmpty() || phone.isEmpty()) {
            showAlert("Thông báo", "Vui lòng điền đầy đủ tất cả các trường thông tin!", Alert.AlertType.WARNING);
            return;
        }

        // Đóng gói đầy đủ chuỗi gói tin (gồm 7 phần tử phân tách bởi dấu ";") bắn lên Server
        if (out != null) {
            out.println("UPDATE_PROFILE;" + currentUsername + ";" + fullName + ";" + dob + ";" + university + ";" + email + ";" + phone);
            showAlert("Thành công", "Đã gửi yêu cầu cập nhật lên hệ thống!", Alert.AlertType.INFORMATION);
            handleClose(event);
        }
    }

    @FXML
    public void handleClose(ActionEvent event) {
        // Tắt cửa sổ Popup hiện tại
        Stage stage = (Stage) txtFullName.getScene().getWindow();
        stage.close();
    }

    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}